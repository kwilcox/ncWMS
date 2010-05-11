/*
 * Applied Science Associates, Inc.
 * Copyright 2009. All Rights Reserved.
 *
 * VectorFactory.java
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
public class VectorFactory {
    private static List<Path2D> vectors;

    static {
        vectors = new ArrayList<Path2D>();
        vectors.add(stumpyVector());
        vectors.add(triangleVector());
        vectors.add(lineVector());
        vectors.add(fancyVector());
    }

    public VectorFactory() {

    }

    public static Path2D getVector(String style, double speed, double angle, int i, int j, double scale) {

        int type = 0;
        if (style.equalsIgnoreCase("STUMPVEC")) {
            type = 0;
        } else if (style.equalsIgnoreCase("TRIVEC")) {
            type = 1;
        } else if (style.equalsIgnoreCase("LINEVEC")) {
            type = 2;
        } else if (style.equalsIgnoreCase("FANCYVEC")) {
            type = 3;
        }

        Path2D ret = (Path2D)vectors.get(type).clone();
        /* Rotate and set position */
        ret.transform(AffineTransform.getRotateInstance(angle));
        ret.transform(AffineTransform.getTranslateInstance(i, j));
        //ret.transform(AffineTransform.getScaleInstance(scale, scale));
        return ret;
    }

    private static Path2D stumpyVector() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,1);
        path.lineTo(0,-1);
        path.lineTo(4,-1);
        path.lineTo(4,-4);
        path.lineTo(10,0);
        path.lineTo(4,4);
        path.lineTo(4,1);
        path.closePath();

        return path;
    }

    private static Path2D triangleVector() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,4);
        path.lineTo(0,-4);
        path.lineTo(10,0);
        path.closePath();

        return path;
    }

    private static Path2D lineVector() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.lineTo(10,0);
        path.moveTo(10,0);
        path.lineTo(6,2.5);
        path.moveTo(10,0);
        path.lineTo(6,-2.5);
        path.closePath();
        
        return path;
    }

    private static Path2D fancyVector() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.lineTo(0,-3);
        path.lineTo(5,-2);
        path.lineTo(3,-5);
        path.lineTo(11,-1.5);
        path.lineTo(3,2);
        path.lineTo(5,-1);
        path.closePath();
        return path;
    }
}
