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
        vectors.add(polygonVector());
    }

    public VectorFactory() {

    }

    public static Path2D getVector(int type, double speed, double angle, int i, int j) {
        Path2D ret = (Path2D)vectors.get(type).clone();
        /* Rotate and set position */
        ret.transform(AffineTransform.getRotateInstance(angle));
        ret.transform(AffineTransform.getTranslateInstance(i, j));
        return ret;
    }

    private static Path2D stumpyVector() {
        Path2D path = new Path2D.Double();
        path.moveTo(6,4);
        path.lineTo(0,0);
        path.lineTo(6,-4);
        path.moveTo(6,0.5);
        path.lineTo(10,0.5);
        path.lineTo(10,-0.5);
        path.lineTo(6,-0.5);
        path.closePath();

        return path;
    }

    private static Path2D triangleVector() {
        Path2D path = new Path2D.Double();
        path.moveTo(10,4);
        path.lineTo(0,0);
        path.lineTo(10,-4);
        path.closePath();

        return path;
    }

    private static Path2D lineVector() {
        Path2D path = new Path2D.Double();
        path.moveTo(4,4);
        path.lineTo(0,0);
        path.lineTo(4,-4);
        path.moveTo(0, 0);
        path.lineTo(10,0);
        path.closePath();
        
        return path;
    }

    private static Path2D polygonVector() {
        Path2D path = new Path2D.Double();
        path.moveTo(0,0);
        path.lineTo(4,-3);
        path.lineTo(4,-0.75);
        path.lineTo(10,-0.75);
        path.lineTo(10,0.75);
        path.lineTo(4,0.75);
        path.lineTo(4,3);
        path.lineTo(0,0);
        path.closePath();
        
        return path;
    }
}
