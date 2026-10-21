package org.ursc.trajectory.app.command;
import org.ursc.trajectory.forces.drag.SpaceWeatherProvider;
import org.ursc.trajectory.forces.gravity.GravityFieldFactory;
import org.ursc.trajectory.frames.*;
import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.orbits.CartesianOrbit;
import org.ursc.trajectory.orbits.PVCoordinates;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.time.AbsoluteDate;
import java.nio.file.Paths;
import java.util.*;
/** Gravity degree/order sweep on the Aug-1 arc, JB2008 + tight integrator + forecast weather,
 *  EIGEN-6S at each (degree,order). args: sw eop eigen utc x y z vx vy vz tx ty tz */
public final class GravSweep2 {
  static Frame gcrf,itrf; static SpacecraftState seed; static SpaceWeatherProvider fc; static AbsoluteDate e8; static Vector3D t8; static String eigen;
  static final class TF implements SpaceWeatherProvider{AbsoluteDate a;double f,fa,p,t=4;TF(AbsoluteDate A,double F,double FA,double P){a=A;f=F;fa=FA;p=P;}
    public double getDailyF107(AbsoluteDate d){double dt=d.durationFrom(a)/86400.0;return dt<=0?f:fa+(f-fa)*Math.exp(-dt/t);}
    public double getAverageF107(AbsoluteDate d){return fa;} public double getDailyAp(AbsoluteDate d){return p;}}
  public static void main(String[] a) throws Exception{
    eigen=a[2]; AbsoluteDate ep=PredictCommand.parseUtc(a[3]);
    List<AbsoluteDate> dl=new ArrayList<>();dl.add(ep);
    List<Vector3D> pl=new ArrayList<>();pl.add(new Vector3D(x(a,4),x(a,5),x(a,6)));
    List<Vector3D> vl=new ArrayList<>();vl.add(new Vector3D(x(a,7),x(a,8),x(a,9)));
    OrbitFiles.configureFrames(PoeOrbReader.of(dl,pl,vl,0.0),OrbitFiles.loadEop(a[1],PoeOrbReader.of(dl,pl,vl,0.0)));
    gcrf=FramesFactory.getGCRF();itrf=FramesFactory.getITRF();
    Transform i2g=FramesFactory.getTransform(itrf,gcrf,ep);
    Vector3D rG=i2g.transformPosition(new Vector3D(x(a,4),x(a,5),x(a,6)));
    Vector3D vG=i2g.transformVelocity(new Vector3D(x(a,7),x(a,8),x(a,9)),rG);
    seed=new SpacecraftState(new CartesianOrbit(new PVCoordinates(rG,vG),gcrf,ep,Constants.EARTH_MU),1130.0);
    OrbitFiles.Setup tmp=new OrbitFiles.Setup();SpaceWeatherProvider obs=OrbitFiles.loadSpaceWeather(a[0],tmp);
    fc=new TF(ep,obs.getDailyF107(ep),obs.getAverageF107(ep),obs.getDailyAp(ep));
    e8=ep.shiftedBy(7*86400.0); t8=new Vector3D(x(a,10),x(a,11),x(a,12));
    System.out.printf(Locale.US,"Gravity degree/order sweep (EIGEN-6S), Aug-1, JB2008+tight+forecast:%n%10s %14s%n","deg x ord","7-day err(m)");
    int[][] combos={{20,20},{40,40},{60,60},{80,80},{100,100},{120,120},{70,40},{120,70}};
    for(int[] c: combos) run(c[0],c[1]);
  }
  static void run(int deg,int ord) throws Exception{
    OrbitFiles.Setup s=new OrbitFiles.Setup();s.mass=1130;s.dragArea=8;s.srpArea=8;s.gravityDegree=deg;s.spaceWeather=fc;
    s.integAbsTol=1e-10;s.integRelTol=1e-12;s.integMaxStep=30;
    s.jb2008Inputs=org.ursc.trajectory.forces.drag.SolfsmyDtcProvider.bundled();
    s.gravityOverride=GravityFieldFactory.loadIcgem(Paths.get(eigen),deg,ord);
    OrbitFiles.ForceSet f=new OrbitFiles.ForceSet();f.gravityDegree=deg;f.gravityOrder=ord;f.sun=true;f.moon=true;
    f.atmosphere="jb2008";f.srp=true;f.solidTides=true;f.oceanTides=true;f.relativity=true;f.albedo=true;
    SpacecraftState fin=((NumericalPropagator)OrbitFiles.config(f,seed,s,2.2,s.cr).build()).propagate(e8);
    double err=FramesFactory.getTransform(gcrf,itrf,fin.getDate()).transformPosition(fin.getPosition()).subtract(t8).getNorm();
    System.out.printf(Locale.US,"%6dx%-4d %,14.1f%n",deg,ord,err);
  }
  static double x(String[] a,int i){return Double.parseDouble(a[i]);} private GravSweep2(){}
}
