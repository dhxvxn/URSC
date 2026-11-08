package org.ursc.trajectory.app.command;
import java.nio.file.*;
import java.util.*;
import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.frames.*;
/** Evaluation only. Run after all independent prediction processes finish. */
public final class ScoreForecasts {
 record PV(Vector3D r,Vector3D v){}
 static Vector3D vector(String[] x,int i){return new Vector3D(Double.parseDouble(x[i]),Double.parseDouble(x[i+1]),Double.parseDouble(x[i+2]));}
 static double[] error(String[] predicted,PV truth){
  Vector3D rp=vector(predicted,2),vp=vector(predicted,5),delta=rp.subtract(truth.r());
  var date=PredictCommand.parseUtc(predicted[0].replace(" UTC",""));
  var t=FramesFactory.getTransform(FramesFactory.getITRF(),FramesFactory.getGCRF(),date);
  var r=t.transformPosition(truth.r());var v=t.transformVelocity(truth.v(),r);var dr=t.transformVector(delta);
  var radial=r.normalize();var normal=r.crossProduct(v).normalize();var along=normal.crossProduct(radial);
  return new double[]{delta.getNorm(),vp.subtract(truth.v()).getNorm(),dr.dotProduct(radial),dr.dotProduct(along),dr.dotProduct(normal)};
 }
 public static void main(String[] argv)throws Exception {
  Locale.setDefault(Locale.US);Path root=Paths.get("docs/reports/two-month-forecast-analytics");
  var dirs=Files.list(root.resolve("results")).filter(Files::isDirectory).sorted().toList();
  Set<String> wanted=new HashSet<>();
  for(var dir:dirs)for(String file:List.of("forecast.csv","spp.csv"))if(Files.exists(dir.resolve(file))){
   var lines=Files.readAllLines(dir.resolve(file));for(String row:lines.subList(1,lines.size()))wanted.add(row.split(",")[0]);
  }
  Map<String,PV> truth=new HashMap<>();
  for(String filename:Files.readAllLines(root.resolve("truth-files.txt"))){
   if(filename.isBlank())continue;var ep=PoeOrbReader.read(Paths.get(filename));
   for(int i=0;i<ep.size();i++){String key=ep.dates.get(i).toString();if(wanted.contains(key))truth.put(key,new PV(ep.positionItrf.get(i),ep.velocityItrf.get(i)));}
  }
  StringBuilder endpoints=new StringBuilder("date,elapsed_days,position_error_m,velocity_error_ms,radial_m,along_track_m,cross_track_m,status\n");
  StringBuilder summary=new StringBuilder("date,solved_epochs,fit_rms_m,spp_rms_m,cutoff_position_error_m,day1_error_m,day7_error_m,day1_status,day7_status\n");
  Path driver=Paths.get("docs/reports/perturbation-ladder-truestate-7day/inputs/finals2000A.all");
  for(var dir:dirs){
   String day=dir.getFileName().toString();var props=new Properties();
   if(Files.exists(dir.resolve("fit.properties")))try(var reader=Files.newBufferedReader(dir.resolve("fit.properties"))){props.load(reader);}
   double sppSq=0;int sppCount=0,solved=0;
   if(Files.exists(dir.resolve("spp.csv"))){var lines=Files.readAllLines(dir.resolve("spp.csv"));solved=lines.size()-1;
    for(String row:lines.subList(1,lines.size())){var a=row.split(",");var t=truth.get(a[0]);if(t!=null){sppSq+=vector(a,1).subtract(t.r()).getNormSq();sppCount++;}}
   }
   String[] values={"","",""};String[] statuses={"NO_FORECAST","NO_FORECAST","NO_FORECAST"};
   if(Files.exists(dir.resolve("forecast.csv"))){
    var cutoff=PredictCommand.parseUtc(props.getProperty("cutoff").replace(" UTC",""));
    var metadata=PoeOrbReader.of(List.of(cutoff),List.of(Vector3D.ZERO),List.of(Vector3D.ZERO),0);
    OrbitFiles.configureFrames(metadata,OrbitFiles.loadForecastEop(driver.toString(),cutoff,metadata));
    var lines=Files.readAllLines(dir.resolve("forecast.csv"));
    for(String row:lines.subList(1,lines.size())){
     var a=row.split(",");double days=Double.parseDouble(a[1]);if(days!=0 && days!=1 && days!=7)continue;
     int slot=days==0?0:days==1?1:2;var t=truth.get(a[0]);
     if(t==null){statuses[slot]="NO_REFERENCE";endpoints.append(day+","+days+",,,,,,NO_REFERENCE\n");continue;}
     var e=error(a,t);values[slot]=String.format("%.9f",e[0]);statuses[slot]="SCORED";
     endpoints.append(String.format("%s,%.0f,%.9f,%.9f,%.9f,%.9f,%.9f,SCORED%n",day,days,e[0],e[1],e[2],e[3],e[4]));
    }
   }
   summary.append(String.format("%s,%d,%s,%s,%s,%s,%s,%s,%s%n",day,solved,props.getProperty("postfit_rms_m",""),sppCount==0?"":String.format("%.9f",Math.sqrt(sppSq/sppCount)),values[0],values[1],values[2],statuses[1],statuses[2]));
  }
  Files.writeString(root.resolve("endpoint-errors.csv"),endpoints);Files.writeString(root.resolve("daily-errors.csv"),summary);
  System.out.println("Scored saved forecasts; reference loaded only in this separate evaluation process.");
 }
}
