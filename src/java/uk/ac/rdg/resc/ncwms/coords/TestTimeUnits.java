/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.ncwms.coords;

import ucar.nc2.units.DateUnit;

/**
 *
 * @author Jon
 */
public class TestTimeUnits {
    public static void main(String[] args) throws Exception {
        DateUnit dateUnit = new DateUnit("days since 2000-02-30 01:33:00 ");
        System.out.println(dateUnit.getTimeUnit());
        System.out.println(dateUnit.getDateOrigin());
    }
}
