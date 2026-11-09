package org.ursc.trajectory.io;

import java.util.List;
import java.util.Locale;

/**
 * KML (Google Earth) and CZML (Cesium) exports of a sampled geodetic track. These complement
 * the CCSDS OEM, which remains the authoritative output.
 */
public final class TrackExport {

    private TrackExport() {
    }

    private static String esc(final String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * @param epochs ISO-8601 UTC epochs (no zone suffix)
     * @param lat    geodetic latitude (deg)
     * @param lon    longitude (deg)
     * @param altKm  height above the ellipsoid (km)
     */
    public static String kml(final String name, final List<String> epochs, final List<Double> lat,
                             final List<Double> lon, final List<Double> altKm) {
        final int step = Math.max(1, epochs.size() / 5000);
        final StringBuilder c = new StringBuilder();
        final StringBuilder when = new StringBuilder();
        final StringBuilder coord = new StringBuilder();
        for (int i = 0; i < epochs.size(); i += step) {
            final double h = altKm.get(i) * 1000.0;
            c.append(String.format(Locale.US, "%.5f,%.5f,%.1f ", lon.get(i), lat.get(i), h));
            when.append("<when>").append(epochs.get(i)).append("Z</when>");
            coord.append(String.format(Locale.US, "<gx:coord>%.5f %.5f %.1f</gx:coord>", lon.get(i), lat.get(i), h));
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:gx=\"http://www.google.com/kml/ext/2.2\">\n<Document>\n"
                + "<name>" + esc(name) + "</name>\n"
                + "<Style id=\"orbit\"><LineStyle><color>fff79c4f</color><width>2</width></LineStyle></Style>\n"
                + "<Style id=\"sat\"><IconStyle><color>ff33ccff</color></IconStyle><LineStyle><color>ff33ccff</color><width>3</width></LineStyle></Style>\n"
                + "<Placemark><name>" + esc(name) + " orbit</name><styleUrl>#orbit</styleUrl><LineString><altitudeMode>absolute</altitudeMode>"
                + "<coordinates>" + c.toString().trim() + "</coordinates></LineString></Placemark>\n"
                + "<Placemark><name>" + esc(name) + "</name><styleUrl>#sat</styleUrl><gx:Track><altitudeMode>absolute</altitudeMode>"
                + when + coord + "</gx:Track></Placemark>\n</Document>\n</kml>\n";
    }

    /** CZML document: one satellite with time-tagged cartographic positions, a path and a label. */
    public static String czml(final String name, final List<String> epochs, final List<Double> t,
                              final List<Double> lat, final List<Double> lon, final List<Double> altKm) {
        final String start = epochs.get(0) + "Z";
        final String end = epochs.get(epochs.size() - 1) + "Z";
        final StringBuilder pos = new StringBuilder();
        for (int i = 0; i < epochs.size(); i++) {
            if (i > 0) {
                pos.append(',');
            }
            pos.append(String.format(Locale.US, "%.1f,%.5f,%.5f,%.1f", t.get(i), lon.get(i), lat.get(i), altKm.get(i) * 1000.0));
        }
        final String n = name.replace("\\", "\\\\").replace("\"", "\\\"");
        return "[{\"id\":\"document\",\"name\":\"" + n + "\",\"version\":\"1.0\",\"clock\":{\"interval\":\"" + start + "/" + end
                + "\",\"currentTime\":\"" + start + "\",\"multiplier\":60}},\n"
                + "{\"id\":\"satellite\",\"name\":\"" + n + "\",\"availability\":\"" + start + "/" + end + "\","
                + "\"label\":{\"text\":\"" + n + "\",\"font\":\"12pt sans-serif\",\"pixelOffset\":{\"cartesian2\":[12,0]}},"
                + "\"point\":{\"pixelSize\":8,\"color\":{\"rgba\":[255,204,51,255]}},"
                + "\"path\":{\"width\":2,\"leadTime\":0,\"trailTime\":5400,\"material\":{\"solidColor\":{\"color\":{\"rgba\":[79,156,247,255]}}}},"
                + "\"position\":{\"epoch\":\"" + start + "\",\"interpolationAlgorithm\":\"LAGRANGE\",\"interpolationDegree\":5,"
                + "\"cartographicDegrees\":[" + pos + "]}}]\n";
    }
}
