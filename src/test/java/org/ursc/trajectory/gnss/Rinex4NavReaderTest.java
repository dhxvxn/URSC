package org.ursc.trajectory.gnss;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
class Rinex4NavReaderTest {
 static String header(String version){return String.format(Locale.US,"%9s           NAVIGATION DATA     M                   RINEX VERSION / TYPE%n",version)+"                                                            END OF HEADER\n";}
 static String number(double x){return String.format(Locale.US,"%19.12E",x).replace('E','D');}
 static String gps(){
  StringBuilder s=new StringBuilder("G01 2024 09 01 00 00 00");s.append(number(1e-4)).append(number(0)).append(number(0)).append('\n');
  double[][] values={{1,0,0,.1},{0,.004,0,5153.65},{0,0,.5,0},{.9,0,.2,0},{0,0,2330,0},{2,0,0,0},{0,4,0,0}};
  for(double[] row:values){s.append("    ");for(double x:row)s.append(number(x));s.append('\n');}return s.toString();
 }
 static RinexNavReader.Data read(String text)throws IOException{return RinexNavReader.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.US_ASCII)));}
 @Test void v3AndV402HaveIdenticalOrbitAndClock()throws Exception{
  var a=read(header("3.05")+gps());
  String text=header("4.02")+"> STO G01 LNAV\nignored\n> EPH G01 CNAV\nignored\n> EPH E01 INAV\nignored\n> EPH G01 LNAV\n"+gps()+"> ION G01 LNAV\nignored\n";
  var b=read(text);assertEquals(1,b.messageCount());
  var t=new org.ursc.trajectory.time.AbsoluteDate(2024,9,1,1,0,0,org.ursc.trajectory.time.TimeScalesFactory.getGPS());
  var ea=a.ephemerisFor(1,t);var eb=b.ephemerisFor(1,t);
  assertEquals(0,ea.positionEcef(t).distance(eb.positionEcef(t)),0);
  assertEquals(ea.clockBias(t),eb.clockBias(t),0);
 }
 @Test void failsClearlyOnUnsupportedOrBrokenInput(){
  for(String text:List.of(header("2.11")+gps(),header("4.03")+gps(),header("4.02")+"> EPH G01 CNAV\n"+gps(),
      header("4.02")+"> EPH G02 LNAV\n"+gps(),header("4.02")+"> EPH G01 LNAV\n"+gps().substring(0,150),
      header("4.02")+"> EPH G01 LNAV\n"+gps()+"extra\n",header("4.02")+gps(),""))
   assertThrows(IOException.class,()->read(text));
 }
 @Test void uploadedFilesHaveExpectedMessageCountsAndFiniteOrbits()throws Exception{
  int[] expected={422,417,417,355,410,426,429,430,422,429,421,426,429,422,420,426,419,442,423,426,429,420,424,423,420,423,429,422,426,423,424,419,422,419,425};
  int[] days={245,246,247,248,249,250,251,252,253,254,255,256,257,258,259,260,261,262,263,264,265,266,267,268,269,270,271,272,273,274,275,276,277,278,279};
  Path root=Paths.get("data/gnss/navigation/2024");
  for(int i=0;i<expected.length;i++){
   var file=root.resolve("BRD400DLR_S_2024"+days[i]+"0000_01D_MN.rnx.gz");
   var data=RinexNavReader.read(file);assertEquals(expected[i],data.messageCount(),file.toString());assertEquals(32,data.prns().size());
   var t=new org.ursc.trajectory.time.AbsoluteDate(2024,1,1,12,0,0,org.ursc.trajectory.time.TimeScalesFactory.getGPS()).shiftedBy((days[i]-1)*86400.0);
   for(int prn:data.prns()) {
    var e=data.ephemerisFor(prn,t);double r=e.positionEcef(t).getNorm();
    assertTrue(r>2e7 && r<3e7);assertTrue(Double.isFinite(e.clockBias(t)));
   }
  }
 }
}
