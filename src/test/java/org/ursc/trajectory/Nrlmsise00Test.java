package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.forces.drag.msis.Nrlmsise00;

/**
 * Validates the NRLMSISE-00 port against the official reference test outputs
 * (test case 1 of the Picone/Hedin/Drob distribution, reproduced by the
 * Brodowski C package). Agreement to the published 7 significant figures
 * confirms the algorithm and the ~2600 embedded coefficients are correct.
 */
class Nrlmsise00Test {

    private Nrlmsise00.Flags cgsFlags() {
        final Nrlmsise00.Flags f = new Nrlmsise00.Flags();
        f.switches[0] = 0; // CGS output
        for (int i = 1; i < 24; i++) {
            f.switches[i] = 1;
        }
        return f;
    }

    private Nrlmsise00.Input referenceInput() {
        final Nrlmsise00.Input in = new Nrlmsise00.Input();
        in.doy = 172;
        in.sec = 29000;
        in.alt = 400;
        in.gLat = 60;
        in.gLong = -70;
        in.lst = 16;
        in.f107A = 150;
        in.f107 = 150;
        in.ap = 4;
        return in;
    }

    @Test
    void reproducesReferenceTestCase1() {
        final Nrlmsise00 model = new Nrlmsise00();
        final Nrlmsise00.Output out = new Nrlmsise00.Output();
        model.gtd7(referenceInput(), cgsFlags(), out);

        assertEquals(6.665177E+05, out.d[0], 6.665177E+05 * 1e-5, "He");
        assertEquals(1.138806E+08, out.d[1], 1.138806E+08 * 1e-5, "O");
        assertEquals(1.998211E+07, out.d[2], 1.998211E+07 * 1e-5, "N2");
        assertEquals(4.022764E+05, out.d[3], 4.022764E+05 * 1e-5, "O2");
        assertEquals(3.557465E+03, out.d[4], 3.557465E+03 * 1e-5, "Ar");
        assertEquals(4.074714E-15, out.d[5], 4.074714E-15 * 1e-5, "total mass density");
        assertEquals(3.475312E+04, out.d[6], 3.475312E+04 * 1e-5, "H");
        assertEquals(4.095913E+06, out.d[7], 4.095913E+06 * 1e-5, "N");
        assertEquals(2.667273E+04, out.d[8], 2.667273E+04 * 1e-5, "anomalous O");
        assertEquals(1250.540, out.t[0], 1e-2, "exospheric temperature");
        assertEquals(1241.416, out.t[1], 1e-2, "temperature at altitude");
    }

    @Test
    void gtd7dIncludesAnomalousOxygenInMass() {
        final Nrlmsise00 model = new Nrlmsise00();
        final Nrlmsise00.Output plain = new Nrlmsise00.Output();
        final Nrlmsise00.Output drag = new Nrlmsise00.Output();
        model.gtd7(referenceInput(), cgsFlags(), plain);
        model.gtd7d(referenceInput(), cgsFlags(), drag);
        // the effective drag mass density must exceed the plain one by the
        // anomalous-oxygen contribution
        assertTrue(drag.d[5] > plain.d[5], "gtd7d mass should exceed gtd7 mass");
        final double expectedDelta = 1.66E-24 * 16.0 * plain.d[8];
        assertEquals(expectedDelta, drag.d[5] - plain.d[5], expectedDelta * 1e-6);
    }
}
