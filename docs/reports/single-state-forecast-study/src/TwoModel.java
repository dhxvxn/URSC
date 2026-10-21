package org.ursc.trajectory.app.command;
import org.ursc.trajectory.forces.drag.SpaceWeatherProvider;
import org.ursc.trajectory.frames.*;
import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.orbits.CartesianOrbit;
import org.ursc.trajectory.orbits.PVCoordinates;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.time.AbsoluteDate;
import java.util.*;
/** Both density models over one arc, forecast weather from the seed instant.
 *  args: sw eop utc x y z vx vy vz horizonDays tx ty tz */
public final class TwoModel {
  static Frame gcrf,itrf; static SpacecraftState seed; static SpaceWeatherProvider fc; static AbsoluteDate end; static Vector3D truth;
  static final class TF implements SpaceWeatherProvider{AbsoluteDate a;double f,fa,p,t=4;TF(AbsoluteDate A,double F,double FA,double P){a=A;f=F;fa=FA;p=P;}
    public double getDailyF107(AbsoluteDate d){double dt=d.durationFrom(a)/86400.0;return dt<=0?f:fa+(f-fa)*Math.exp(-dt/t);}
    public double getAverageF107(AbsoluteDate d){return fa;} public double getDailyAp(AbsoluteDate d){return p;}}
  public static void main(String[] a) throws Exception{
    AbsoluteDate ep=PredictCommand.parseUtc(a[2]);
    List<AbsoluteDate> dl=new ArrayList<>();dl.add(ep);
    List<Vector3D> pl=new ArrayList<>();pl.add(new Vector3D(x(a,3),x(a,4),x(a,5)));
    List<Vector3D> vl=new ArrayList<>();vl.add(new Vector3D(x(a,6),x(a,7),x(a,8)));
    OrbitFiles.configureFrames(PoeOrbReader.of(dl,pl,vl,0.0),OrbitFiles.loadEop(a[1],PoeOrbReader.of(dl,pl,vl,0.0)));
    gcrf=FramesFactory.getGCRF();itrf=FramesFactory.getITRF();
    Transform i2g=FramesFactory.getTransform(itrf,gcrf,ep);
    Vector3D rG=i2g.transformPosition(new Vector3D(x(a,3),x(a,4),x(a,5)));
    Vector3D vG=i2g.transformVelocity(new Vector3D(x(a,6),x(a,7),x(a,8)),rG);
    seed=new SpacecraftState(new CartesianOrbit(new PVCoordinates(rG,vG),gcrf,ep,Constants.EARTH_MU),1130.0);
    OrbitFiles.Setup tmp=new OrbitFiles.Setup();SpaceWeatherProvider obs=OrbitFiles.loadSpaceWeather(a[0],tmp);
    fc=new TF(ep,obs.getDailyF107(ep),obs.getAverageF107(ep),obs.getDailyAp(ep));
    double days=Double.parseDouble(a[9]); end=ep.shiftedBy(days*86400.0);
    truth=new Vector3D(x(a,10),x(a,11),x(a,12));
    System.out.printf(Locale.US,"Seed %s -> +%.0f days. Seed weather F10.7=%.1f F10.7a=%.1f Ap=%.1f%n%n",
      ep, days, obs.getDailyF107(ep), obs.getAverageF107(ep), obs.getDailyAp(ep));
    System.out.printf(Locale.US,"%-40s %14s%n","config (forecast weather, A=8, Cd 2.2)", days+"-day err (m)");
    run("NRLMSISE, loose","nrlmsise00",false);
    run("NRLMSISE, tight","nrlmsise00",true);
    run("JB2008, loose","jb2008",false);
    run("JB2008, tight","jb2008",true);
  }
  static void run(String label,String atmos,boolean tight){
    OrbitFiles.Setup s=new OrbitFiles.Setup();s.mass=1130;s.dragArea=8;s.srpArea=8;s.gravityDegree=70;s.spaceWeather=fc;
    if(tight){s.integAbsTol=1e-10;s.integRelTol=1e-12;s.integMaxStep=30;}
    if("jb2008".equals(atmos)) s.jb2008Inputs=org.ursc.trajectory.forces.drag.SolfsmyDtcProvider.bundled();
    OrbitFiles.ForceSet f=new OrbitFiles.ForceSet();f.gravityDegree=70;f.gravityOrder=70;f.sun=true;f.moon=true;
    f.atmosphere=atmos;f.srp=true;f.solidTides=true;f.oceanTides=true;f.relativity=true;f.albedo=true;
    SpacecraftState fin=((NumericalPropagator)OrbitFiles.config(f,seed,s,2.2,s.cr).build()).propagate(end);
    double err=FramesFactory.getTransform(gcrf,itrf,fin.getDate()).transformPosition(fin.getPosition()).subtract(truth).getNorm();
    System.out.printf(Locale.US,"%-40s %,14.1f%n",label,err);
  }
  static double x(String[] a,int i){return Double.parseDouble(a[i]);} private TwoModel(){}
}
