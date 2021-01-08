package de.embl.cba.spindle3d;

import net.imglib2.*;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

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

	public static <T extends RealType<T> > Pair<Double, Double> getMinMaxValues( RandomAccessibleInterval<T> rai, RandomAccessibleInterval< UnsignedByteType > mask )
	{
		final Cursor<T> cursor = Views.iterable( rai ).localizingCursor();
		final RandomAccess< UnsignedByteType > maskAccess = mask.randomAccess();

		double maxValue = -1.7976931348623157E308D;
		double minValue = 1.7976931348623157E308D;

		while( cursor.hasNext() )
		{
			cursor.fwd();
			if ( maskAccess.setPositionAndGet( cursor ).get() > 0 )
			{
				double value = cursor.get().getRealDouble();
				if ( value > maxValue ) maxValue = value;
				if ( value < minValue ) minValue = value;
			}
			else
			{
				int a = 1;
			}
		}

		return new ValuePair( minValue, maxValue );
	}
}
