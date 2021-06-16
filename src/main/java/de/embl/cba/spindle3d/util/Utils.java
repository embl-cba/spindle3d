package de.embl.cba.spindle3d.util;

import ij.ImagePlus;
import ij.measure.Calibration;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.view.Views;

public class Utils
{
	public static ImagePlus asImagePlus( RandomAccessibleInterval rai, String title )
	{
		final ImagePlus wrap = ImageJFunctions.wrap(
				Views.permute(
						Views.addDimension( rai, 0, 0 ),
						2, 3 ), title );
		return wrap;
	}

	public static ImagePlus asImagePlus( RandomAccessibleInterval rai, String title, Calibration calibration )
	{
		final ImagePlus wrap = ImageJFunctions.wrap(
				Views.permute(
						Views.addDimension( rai, 0, 0 ),
						2, 3 ), title );

		wrap.setCalibration( calibration );
		return wrap;
	}



}
