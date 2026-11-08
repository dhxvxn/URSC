package org.ursc.trajectory.app.command;
import java.nio.file.*;
import java.util.*;
import org.ursc.trajectory.app.Args;
import org.ursc.trajectory.estimation.*;
import org.ursc.trajectory.gnss.*;
import org.ursc.trajectory.frames.*;
import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.*;
import org.ursc.trajectory.orbits.*;
import org.ursc.trajectory.propagation.*;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;
/** No truth file is accepted. Settings frozen before scoring. */
public final class DailyForecast {
 public static void main(String[] argv)throws Exception {
  Locale.setDefault(Locale.US);Path out=Paths.get(argv[2]);Files.createDirectories(out);long begin=System.nanoTime();
  String day=argv[3]; var midnight=PredictCommand.parseUtc(day+"T00:00:00").shiftedBy(-18);
  var cutoff=midnight.shiftedBy(21600);
  var data=SppCommand.readObs(Paths.get(argv[0]),300);
  var nav=RinexNavReader.read(Paths.get(argv[1])).pastOnly(cutoff);
  var setup=new OrbitFiles.Setup();setup.gravityDegree=70;
  var args=new Args(new String[]{"--accuracy","precise","--scenario","nominal"});setup.configureNumerics(args);
  Path drivers=Paths.get("docs/reports/perturbation-ladder-truestate-7day/inputs");
  setup.spaceWeather=OrbitFiles.loadSpaceWeather(drivers.resolve("SpaceWeather-All-v1.2.txt").toString(),setup);
  OrbitFiles.forecastInputs(args,setup,cutoff);
  var metadata=PoeOrbReader.of(List.of(cutoff),List.of(Vector3D.ZERO),List.of(Vector3D.ZERO),0);
  var eop=OrbitFiles.loadForecastEop(drivers.resolve("finals2000A.all").toString(),cutoff,metadata);
  OrbitFiles.configureFrames(metadata,eop);
  var fits=new ArrayList<PositionObservation>();var engine=new SppEngine();Vector3D guess=Vector3D.ZERO;int attempted=0;
  StringBuilder spp=new StringBuilder("epoch_utc,x_itrf_m,y_itrf_m,z_itrf_m,clock_m,nsat,pdop\n");
  for(var epoch:data.getEpochs()) {
   if(epoch.getDate().durationFrom(midnight)<0 || epoch.getDate().durationFrom(cutoff)>0)continue;attempted++;
   var fix=engine.solveEpoch(epoch,nav,guess);if(fix==null || !fix.isConverged())continue;
   guess=fix.getPosition();var tr=FramesFactory.getTransform(FramesFactory.getITRF(),FramesFactory.getGCRF(),epoch.getDate());
   double[][] q=fix.covariance(5),r=new double[3][3],c=new double[3][3];
   Vector3D[] axes={new Vector3D(1,0,0),new Vector3D(0,1,0),new Vector3D(0,0,1)};
   for(int j=0;j<3;j++){var v=tr.transformVector(axes[j]);r[0][j]=v.getX();r[1][j]=v.getY();r[2][j]=v.getZ();}
   for(int i=0;i<3;i++)for(int j=0;j<3;j++)for(int a=0;a<3;a++)for(int b=0;b<3;b++)c[i][j]+=r[i][a]*q[a][b]*r[j][b];
   fits.add(new PositionObservation(epoch.getDate(),tr.transformPosition(guess),c));
   spp.append(String.format("%s,%.9f,%.9f,%.9f,%.9f,%d,%.6f%n",epoch.getDate(),guess.getX(),guess.getY(),guess.getZ(),fix.getClockBias(),fix.getUsedSatellites(),fix.getPdop()));
  }
  Files.writeString(out.resolve("spp.csv"),spp);System.out.println("SPP solved "+fits.size()+"/"+attempted);
  if(fits.size()<3)throw new IllegalStateException("Insufficient solved SPP epochs");
  var first=fits.get(0);var second=fits.get(1);var third=fits.get(2);
  double t1=second.getDate().durationFrom(first.getDate()),t2=third.getDate().durationFrom(first.getDate());
  var velocity=second.getPosition().subtract(first.getPosition()).scalarMultiply(t2/(t1*(t2-t1))).subtract(third.getPosition().subtract(first.getPosition()).scalarMultiply(t1/(t2*(t2-t1))));
  double[] x={first.getPosition().getX(),first.getPosition().getY(),first.getPosition().getZ(),velocity.getX(),velocity.getY(),velocity.getZ()};
  BatchLeastSquares.PropagatorFactory factory=p->{
   var state=new SpacecraftState(new CartesianOrbit(new PVCoordinates(new Vector3D(p[0],p[1],p[2]),new Vector3D(p[3],p[4],p[5])),FramesFactory.getGCRF(),first.getDate(),Constants.EARTH_MU),setup.mass);
   return (SampledPropagator)OrbitFiles.fullConfig(state,setup,2.2).build();
  };
  var result=new BatchLeastSquares(factory,fits,new double[]{1,1,1,.001,.001,.001}).estimate(x,12);
  System.out.printf("OD: converged=%s iterations=%d postfit=%.6f m%n",result.converged,result.iterations,result.postFitRms);
  Files.writeString(out.resolve("fit.properties"),"cutoff="+cutoff+"\nsolved="+fits.size()+"\nattempted="+attempted+"\nconverged="+result.converged+"\niterations="+result.iterations+"\npostfit_rms_m="+result.postFitRms+"\n");
  if(!result.converged)throw new IllegalStateException("Batch initialization did not converge; no forecast issued");
  var atCutoff=factory.build(result.params).propagate(cutoff);
  var prop=OrbitFiles.fullConfig(atCutoff,setup,2.2).build();var samples=new EphemerisCollector();
  ((SampledPropagator)prop).setStepHandler(86400,samples);prop.propagate(cutoff.shiftedBy(7*86400));
  StringBuilder csv=new StringBuilder("epoch_utc,elapsed_days,x_itrf_m,y_itrf_m,z_itrf_m,vx_itrf_ms,vy_itrf_ms,vz_itrf_ms\n");
  for(var state:samples.getStates()) {
   var tr=FramesFactory.getTransform(state.getFrame(),FramesFactory.getITRF(),state.getDate());var r=tr.transformPosition(state.getPosition());var v=tr.transformVelocity(state.getVelocity(),r);
   csv.append(String.format("%s,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f%n",state.getDate(),state.getDate().durationFrom(cutoff)/86400,r.getX(),r.getY(),r.getZ(),v.getX(),v.getY(),v.getZ()));
  }
  Files.writeString(out.resolve("forecast.csv"),csv);
  Files.writeString(out.resolve("environment.properties"),"cutoff="+cutoff+"\nf107="+setup.spaceWeather.getDailyF107(cutoff.shiftedBy(86400))+"\nf107a="+setup.spaceWeather.getAverageF107(cutoff.shiftedBy(86400))+"\nap="+setup.spaceWeather.getDailyAp(cutoff.shiftedBy(86400))+"\ndut1="+eop.getUT1MinusUTC(cutoff)+"\nxp="+eop.getXp(cutoff)+"\nyp="+eop.getYp(cutoff)+"\npublication_vintages_verified=false\n");
  System.out.printf("Completed in %.3f s; truth never opened%n",(System.nanoTime()-begin)/1e9);
 }
}
