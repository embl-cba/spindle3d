package de.embl.cba.spindle3d;

import de.embl.cba.morphometry.Algorithms;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.type.numeric.real.DoubleType;

import java.util.ArrayList;

public class Spindle3DUtils
{
	public static double thresholdOtsu( ArrayList< Double > values )
	{
		ArrayImg< DoubleType, DoubleArray > doubles = ArrayImgs.doubles( values.size() );
		ArrayCursor< DoubleType > cursor = doubles.cursor();
		int i = 0;
		while ( cursor.hasNext() )
		{
			cursor.next().set( values.get( i++ ) );
		}

		return Algorithms.thresholdOtsu( doubles );
	}
}
