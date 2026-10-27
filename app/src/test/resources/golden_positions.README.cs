using System;
using System.Globalization;
using PhysicsLab.Core;
// Golden reference: dump heliocentric positions of all 8 planets on a fixed date grid, using the
// EXACT C# SolarCore the Unity app ships. "R" round-trips doubles bit-exactly.
class Dump {
  static void Main() {
    var ci = CultureInfo.InvariantCulture;
    Console.WriteLine("planet,jd,x,y,z");
    for (int k = 0; k <= 400; k++) {
      double jd = Solar.J2000 - 3650.0 + k * 37.123;      // ~40 years, irregular step
      double T = Solar.CenturiesFromJD(jd);
      for (int i = 0; i < Solar.Planets.Length; i++) {
        Solar.Position(Solar.Planets[i], T, out double x, out double y, out double z);
        Console.WriteLine(string.Join(",", i.ToString(ci), jd.ToString("R", ci), x.ToString("R", ci), y.ToString("R", ci), z.ToString("R", ci)));
      }
    }
    // display-scale reference too, so the visual layer is pinned as well as the physics
    Console.Error.WriteLine("disp:" + Solar.OrbitDisplayRadius(2, Solar.Planets[2].A, 0.4).ToString("R", ci)
      + " sun0:" + Solar.SunDisplayRadius(0).ToString("R", ci));
  }
}
