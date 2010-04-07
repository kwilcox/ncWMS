/*
 * Applied Science Associates, Inc.
 * Copyright 2009. All Rights Reserved.
 *
 * BarbFactory.java
 *
 * Created on Apr 6, 2010 @ 11:12:12 AM
 */

package uk.ac.rdg.resc.ncwms.graphics;

import java.util.List;
import java.util.ArrayList;
import java.awt.geom.Path2D;
import java.awt.geom.AffineTransform;

/**
 *
 * @author CBM <cmueller@asascience.com>
 * @adapted Kyle Wilcox <kwilcox@asascience.com>
 */
public class BarbFactory {
    private static List<Path2D> windBarbs;

    static {
        windBarbs = new ArrayList<Path2D>();
        windBarbs.add(barb_0_4());
        windBarbs.add(barb_5_9());
        windBarbs.add(barb_10_14());
        windBarbs.add(barb_15_19());
        windBarbs.add(barb_20_24());
        windBarbs.add(barb_25_29());
        windBarbs.add(barb_30_34());
        windBarbs.add(barb_35_39());
        windBarbs.add(barb_40_44());
        windBarbs.add(barb_45_49());
        windBarbs.add(barb_50_54());
        windBarbs.add(barb_55_59());
        windBarbs.add(barb_60_64());
        windBarbs.add(barb_65_69());
        windBarbs.add(barb_70_74());
        windBarbs.add(barb_75_79());
        windBarbs.add(barb_80_84());
        windBarbs.add(barb_85_89());
        windBarbs.add(barb_90_94());
        windBarbs.add(barb_95_99());
        windBarbs.add(barb_100());
	}

    public BarbFactory() {

    }

    public static Path2D getWindBarbForSpeed(double speed, double angle, int i, int j, String units) {
        /* Convert to knots */
        if (units.compareTo("m/s") == 0) {
            speed = speed * 1.94384449;
        } else if (units.compareTo("cm/s") == 0) {
           speed = speed * 0.0194384449;
        }

        /* Get index into windBarbs array */
        int rank = (int)(speed / 5) + 1;
        if(rank < 0) {
            rank = 0;
        } else if (rank >= windBarbs.size()) {
            rank = windBarbs.size() - 1;
        }

        Path2D ret = (Path2D)windBarbs.get(rank).clone();
        /* Rotate and set position */
        ret.transform(AffineTransform.getRotateInstance(angle));
        ret.transform(AffineTransform.getTranslateInstance(i, j));
        return ret;
    }

    private static Path2D barb_0_4() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(18,0);
        return path;
    }
    private static Path2D barb_5_9() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(18,0);
        path.moveTo(15,0);
        path.lineTo(17,-8);
        return path;
    }
    private static Path2D barb_10_14() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(18,0);
        path.lineTo(22,-16);
        return path;
    }
    private static Path2D barb_15_19() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(18,0);
        path.lineTo(22,-16);
        path.moveTo(15,0);
        path.lineTo(17,-8);
        return path;
    }
    private static Path2D barb_20_24() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(18,0);
        path.lineTo(22,-16);
        path.moveTo(15,0);
        path.lineTo(19,-16);
        return path;
    }

    /* CONTINUE */
    private static Path2D barb_25_29() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(18,0);
        path.lineTo(22,-16);
        path.moveTo(15,0);
        path.lineTo(19,-16);
        path.moveTo(12,0);
        path.lineTo(14,-8);
        return path;
    }
    private static Path2D barb_30_34() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(18,0);
        path.lineTo(22,-16);
        path.moveTo(15,0);
        path.lineTo(19,-16);
        path.moveTo(12,0);
        path.lineTo(16,-16);
        return path;
    }
    private static Path2D barb_35_39() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(18,0);
        path.lineTo(22,-16);
        path.moveTo(15,0);
        path.lineTo(19,-16);
        path.moveTo(12,0);
        path.lineTo(16,-16);
        path.moveTo(9,0);
        path.lineTo(11,-8);
        return path;
    }
    private static Path2D barb_40_44() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(18,0);
        path.lineTo(22,-16);
        path.moveTo(15,0);
        path.lineTo(19,-16);
        path.moveTo(12,0);
        path.lineTo(16,-16);
        path.moveTo(9,0);
        path.lineTo(13,-16);
        return path;
    }
    private static Path2D barb_45_49() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(18,0);
        path.lineTo(22,-16);
        path.moveTo(15,0);
        path.lineTo(19,-16);
        path.moveTo(12,0);
        path.lineTo(16,-16);
        path.moveTo(9,0);
        path.lineTo(13,-16);
        path.moveTo(6,0);
        path.lineTo(8,-8);
        return path;
    }
    private static Path2D barb_50_54() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(22,0);
        path.lineTo(22,-16);
        path.lineTo(18,0);
        return path;
    }
    private static Path2D barb_55_59() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(22,0);
        path.lineTo(22,-16);
        path.lineTo(18,0);
        path.moveTo(15,0);
        path.lineTo(17,-8);
        return path;
    }
    private static Path2D barb_60_64() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(22,0);
        path.lineTo(22,-16);
        path.lineTo(18,0);
        path.moveTo(15,0);
        path.lineTo(19,-16);
        return path;
    }
    private static Path2D barb_65_69() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(22,0);
        path.lineTo(22,-16);
        path.lineTo(18,0);
        path.moveTo(15,0);
        path.lineTo(19,-16);
        path.moveTo(12,0);
        path.lineTo(14,-8);
        return path;
    }
    private static Path2D barb_70_74() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(22,0);
        path.lineTo(22,-16);
        path.lineTo(18,0);
        path.moveTo(15,0);
        path.lineTo(19,-16);
        path.moveTo(12,0);
        path.lineTo(16,-16);
        return path;
    }
    private static Path2D barb_75_79() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(22,0);
        path.lineTo(22,-16);
        path.lineTo(18,0);
        path.moveTo(15,0);
        path.lineTo(19,-16);
        path.moveTo(12,0);
        path.lineTo(16,-16);
        path.moveTo(9,0);
        path.lineTo(11,-8);
        return path;
    }
    private static Path2D barb_80_84() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(22,0);
        path.lineTo(22,-16);
        path.lineTo(18,0);
        path.moveTo(15,0);
        path.lineTo(19,-16);
        path.moveTo(12,0);
        path.lineTo(16,-16);
        path.moveTo(9,0);
        path.lineTo(13,-16);
        return path;
    }
    private static Path2D barb_85_89() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(22,0);
        path.lineTo(22,-16);
        path.lineTo(18,0);
        path.moveTo(15,0);
        path.lineTo(19,-16);
        path.moveTo(12,0);
        path.lineTo(16,-16);
        path.moveTo(9,0);
        path.lineTo(13,-16);
        path.moveTo(6,0);
        path.lineTo(8,-8);
        return path;
    }
    private static Path2D barb_90_94() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(22,0);
        path.lineTo(22,-16);
        path.lineTo(18,0);
        path.moveTo(15,0);
        path.lineTo(19,-16);
        path.moveTo(12,0);
        path.lineTo(16,-16);
        path.moveTo(9,0);
        path.lineTo(13,-16);
        path.moveTo(6,0);
        path.lineTo(10,-16);
        return path;
    }
    private static Path2D barb_95_99() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(22,0);
        path.lineTo(22,-16);
        path.lineTo(18,0);
        path.moveTo(15,0);
        path.lineTo(19,-16);
        path.moveTo(12,0);
        path.lineTo(16,-16);
        path.moveTo(9,0);
        path.lineTo(13,-16);
        path.moveTo(6,0);
        path.lineTo(10,-16);
        path.moveTo(3,0);
        path.lineTo(5,-8);
        return path;
    }
    private static Path2D barb_100() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.quadTo(-2,2,-4,0);
        path.quadTo(-2,-2,0,0);
        path.lineTo(22,0);
        path.lineTo(22,-16);
        path.lineTo(14,-16);
        path.lineTo(14,0);
        return path;
    }
}
