import java.nio.file.*; import java.util.*;
import org.ursc.trajectory.orekit.*; import org.ursc.trajectory.frames.*;
import org.orekit.estimation.measurements.*; import org.orekit.estimation.measurements.gnss.*;
import org.orekit.orbits.*; import org.orekit.propagation.SpacecraftState; import org.orekit.utils.*;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
public class SpanPartialsReproducer { public static void main(String[] a) throws Exception {
  var d = new org.ursc.trajectory.time.AbsoluteDate(2024,10,15,3,0,0.0, org.ursc.trajectory.time.TimeScalesFactory.getUTC());
  try (OrekitSession s = OrekitSession.open(ConstantEop.ZERO, d.shiftedBy(-86400), d.shiftedBy(86400), null, Path.of("/home/user/URSC/data/ephemeris/lnxp1990.440"))) {
    var t = OrekitDates.toOrekit(d);
    var orbit = new CartesianOrbit(new PVCoordinates(new Vector3D(7.2e6,0,0), new Vector3D(0,7.4e3,0)), s.gcrf, t, Constants.EIGEN5C_EARTH_MU);
    var st = new SpacecraftState(orbit);
    PVCoordinatesProvider gps = (date, frame) -> new TimeStampedPVCoordinates(date, new PVCoordinates(new Vector3D(2.0e7, 1.0e7, 1.0e7), new Vector3D(0, 3000, 0)));
    var sat = new ObservableSatellite(0, "LEO"); sat.getClockOffsetDriver().setSelected(true); for (int i=-2;i<=2;i++) if (i!=0) sat.getClockOffsetDriver().addSpanAtDate(t.shiftedBy(30*i + (i>0?-15:15)));
    var clk = new QuadraticClockModel(t, 1e-4, 0, 0);
    var r = new OneWayGNSSRange(gps, clk, t, 2.0e7, 1.0, 1.0, sat);
    var cache = new AmbiguityCache();
    var p = new OneWayGNSSPhase(gps, "G01", clk, t, 2.0e7, 1.0, 0.01, 1.0, sat, cache);
    p.getAmbiguityDriver().setSelected(true);
    for (ObservedMeasurement<?> m : List.of(r, p)) {
      EstimatedMeasurement<?> e = m.estimate(0, 0, new SpacecraftState[] {st});
      System.out.println(m.getClass().getSimpleName() + " est=" + e.getEstimatedValue()[0]);
      e.getDerivativesDrivers().forEach(dr -> { for (var sp = dr.getNamesSpanMap().getFirstSpan(); sp != null; sp = sp.next()) { var at = sp.getStart().durationFrom(org.orekit.time.AbsoluteDate.PAST_INFINITY) == 0 ? sp.getEnd().shiftedBy(-1) : sp.getStart(); System.out.println("   d/d " + sp.getData() + " [" + sp.getStart() + "] = " + Arrays.toString(e.getParameterDerivatives(dr, sp.getStart()))); } });
      System.out.println("   drivers of measurement: " + m.getParametersDrivers().stream().map(x -> x.getName()+(x.isSelected()?"*":"")).toList());
    }
  }
}}
