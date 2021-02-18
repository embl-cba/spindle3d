package de.embl.cba.spindle3d;

import de.embl.cba.morphometry.Logger;
import de.embl.cba.morphometry.regions.Regions;
import net.imglib2.*;
import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelRegion;
import net.imglib2.roi.labeling.LabelRegionCursor;
import net.imglib2.roi.labeling.LabelRegions;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;


/**
 *
 * TODO: Move all of this to imagej-utils
 *
 */
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

	public static <T extends RealType<T> > Pair<Double, Double> getMinMaxValues( RandomAccessibleInterval<T> rai, RandomAccessibleInterval< BitType > mask )
	{
		final Cursor<T> cursor = Views.iterable( rai ).localizingCursor();
		final RandomAccess< BitType > maskAccess = mask.randomAccess();

		double maxValue = -1.7976931348623157E308D;
		double minValue = 1.7976931348623157E308D;

		while( cursor.hasNext() )
		{
			cursor.fwd();
			if ( maskAccess.setPositionAndGet( cursor ).get() )
			{
				double value = cursor.get().getRealDouble();
				if ( value > maxValue ) maxValue = value;
				if ( value < minValue ) minValue = value;
			}
		}

		return new ValuePair( minValue, maxValue );
	}

	public static < T extends RealType< T > & NativeType< T > >
	void removeRegion( RandomAccessibleInterval< T > img, LabelRegion labelRegion )
	{
		final Cursor regionCursor = labelRegion.cursor();
		final RandomAccess< T > access = img.randomAccess();
		while ( regionCursor.hasNext() )
		{
			regionCursor.fwd();
			access.setPosition( regionCursor );
			access.get().setReal( 0 );
		}
	}

	/**
	 *
	 * @param mask
	 * @param borderDimensions 2 = lateral only, 3 = all
	 * @return
	 */
	public static int removeRegionsTouchingImageBorders( RandomAccessibleInterval< BitType > mask, int borderDimensions )
	{
		final ImgLabeling< Integer, IntType > imgLabeling = Regions.asImgLabeling( mask, ConnectedComponents.StructuringElement.EIGHT_CONNECTED );

		final LabelRegions< Integer > labelRegions = new LabelRegions<>( imgLabeling );

		final int size = labelRegions.getExistingLabels().size();
		int numRegionsTouchingBorder = 0;
		for ( LabelRegion labelRegion : labelRegions )
		{
			final LabelRegionCursor cursor = labelRegion.cursor();

			boolean touchesBorder = false;
			while ( cursor.hasNext() )
			{
				cursor.fwd();
				for ( int d = 0; d < borderDimensions; d++ )
				{
					if ( cursor.getIntPosition( d ) == imgLabeling.min( d ) || cursor.getIntPosition( d ) == imgLabeling.max( d ) )
					{
						touchesBorder = true;
					}
				}

				if ( touchesBorder )
				{
					numRegionsTouchingBorder++;
					removeRegion( mask, labelRegion );
					break;
				}
			}
		}

		Logger.log( "Removed " + numRegionsTouchingBorder + " of " + size + " regions, because of image border contact." );
		return size - numRegionsTouchingBorder;
	}
}
