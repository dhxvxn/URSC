package org.ursc.trajectory.app.command;
import org.ursc.trajectory.bodies.CelestialBodyFactory;
import org.ursc.trajectory.bodies.De440Ephemeris;
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
import java.nio.file.Paths;
import java.util.*;
/** Fidelity levers on the single-state JB2008 Aug case: DE440 Sun/Moon, tighter integrator,
 *  and the pure non-drag floor. args: swFile eopFile de440 utc x y z vx vy vz tx ty tz */
public final class FidelityJb {
  static Frame gcrf,itrf; static SpacecraftState seed; static SpaceWeatherProvider fc; static String de440;
  static final class TF implements SpaceWeatherProvider{AbsoluteDate a;double f,fa,p,t=4;TF(AbsoluteDate A,double F,double FA,double P){a=A;f=F;fa=FA;p=P;}
    public double getDailyF107(AbsoluteDate d){double dt=d.durationFrom(a)/86400.0;return dt<=0?f:fa+(f-fa)*Math.exp(-dt/t);}
    public double getAverageF107(AbsoluteDate d){return fa;} public double getDailyAp(AbsoluteDate d){return p;}}
  static AbsoluteDate e8; static Vector3D t8;
  public static void main(String[] a) throws Exception{
    de440=a[2];
    AbsoluteDate ep=PredictCommand.parseUtc(a[3]);
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
    System.out.printf(Locale.US,"JB2008 single-state, 7-day error vs Aug-8 (forecast weather, A=8):%n%-46s %12s%n","config","err (m)");
    run("base (analytic Sun/Moon, EGM96-70, loose tol)", "jb2008",false,1e-7,1e-9,120);
    run("no drag (atmosphere=none) -> non-drag floor",   "none",  false,1e-7,1e-9,120);
    run("+ DE440 Sun/Moon",                               "jb2008",true, 1e-7,1e-9,120);
    run("+ tight integrator (1e-10/1e-12, 30 s)",         "jb2008",false,1e-10,1e-12,30);
    run("+ DE440 + tight integrator",                     "jb2008",true, 1e-10,1e-12,30);
    run("no drag + DE440 + tight (pure non-drag floor)",  "none",  true, 1e-10,1e-12,30);
  }
  static void run(String label,String atmos,boolean useDe,double abs,double rel,double maxStep) throws Exception{
    if(useDe) CelestialBodyFactory.setEphemeris(new De440Ephemeris(Paths.get(de440))); else CelestialBodyFactory.setEphemeris(null);
    OrbitFiles.Setup s=new OrbitFiles.Setup();s.mass=1130;s.dragArea=8;s.srpArea=8;s.gravityDegree=70;s.spaceWeather=fc;
    s.integAbsTol=abs;s.integRelTol=rel;s.integMaxStep=maxStep;
    if("jb2008".equals(atmos)) s.jb2008Inputs=org.ursc.trajectory.forces.drag.SolfsmyDtcProvider.bundled();
    OrbitFiles.ForceSet fs=new OrbitFiles.ForceSet();fs.gravityDegree=70;fs.gravityOrder=70;fs.sun=true;fs.moon=true;
    fs.atmosphere=atmos;fs.srp=true;fs.solidTides=true;fs.oceanTides=true;fs.relativity=true;fs.albedo=true;
    SpacecraftState f=((NumericalPropagator)OrbitFiles.config(fs,seed,s,2.2,s.cr).build()).propagate(e8);
    double err=FramesFactory.getTransform(gcrf,itrf,f.getDate()).transformPosition(f.getPosition()).subtract(t8).getNorm();
    CelestialBodyFactory.setEphemeris(null);
    System.out.printf(Locale.US,"%-46s %,12.1f%n",label,err);
  }
  static double x(String[] a,int i){return Double.parseDouble(a[i]);} private FidelityJb(){}
}
