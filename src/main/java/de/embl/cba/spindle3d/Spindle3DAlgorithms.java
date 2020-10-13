package de.embl.cba.spindle3d;

import net.imglib2.*;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;

public abstract class Spindle3DAlgorithms
{
	public static < T extends RealType< T > & NativeType< T > > RealPoint getMaximumLocationWithinMask(
			IterableInterval< T > ii,
			RandomAccessibleInterval< BitType > mask,
			double[] calibration )
	{
		Cursor< T > cursor = ii.localizingCursor();

		double maxValue = -Double.MAX_VALUE;

		long[] maxLoc = new long[ cursor.numDimensions() ];
		cursor.localize( maxLoc );

		RandomAccess< BitType > maskAccess = mask.randomAccess();

		while ( cursor.hasNext() )
		{
			final double value = cursor.next().getRealDouble();

			if ( maskAccess.setPositionAndGet( cursor ).get() )
			{
				if ( value > maxValue )
				{
					maxValue = value;
					cursor.localize( maxLoc );
				}
			}
		}

		if ( maxValue == -Double.MAX_VALUE )
		{
			throw new RuntimeException( "Could not find maximum within mask." );
		}

		double[] calibratedMaxLoc = new double[ maxLoc.length ];
		for ( int d = 0; d < ii.numDimensions(); ++d )
			if ( calibration != null )
				calibratedMaxLoc[ d ] = maxLoc[ d ] * calibration[ d ];
			else
				calibratedMaxLoc[ d ] = maxLoc[ d ];

		RealPoint point = new RealPoint( calibratedMaxLoc );

		return point;
	}

}
