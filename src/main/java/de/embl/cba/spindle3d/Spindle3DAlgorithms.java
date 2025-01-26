package de.embl.cba.spindle3d;

import de.embl.cba.spindle3d.util.Utils;
import de.embl.cba.spindle3d.util.VectorUtils;
import ij.IJ;
import net.imagej.ops.OpService;
import net.imagej.ops.threshold.otsu.ComputeOtsuThreshold;
import net.imglib2.*;
import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.algorithm.morphology.Dilation;
import net.imglib2.algorithm.morphology.Opening;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.converter.Converters;
import net.imglib2.histogram.Histogram1d;
import net.imglib2.histogram.Real1dBinMapper;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelRegion;
import net.imglib2.roi.labeling.LabelRegionCursor;
import net.imglib2.roi.labeling.LabelRegions;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.*;
import net.imglib2.view.Views;

import java.util.ArrayList;

import static de.embl.cba.spindle3d.util.Utils.asIntegerInterval;
import static de.embl.cba.spindle3d.util.Utils.createScaledInterval;

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
		final ImgLabeling< Integer, IntType > imgLabeling = Utils.asImgLabeling( mask, ConnectedComponents.StructuringElement.EIGHT_CONNECTED );

		final LabelRegions< Integer > labelRegions = new LabelRegions<>( imgLabeling );

		final int size = labelRegions.getExistingLabels().size();
		int numRegionsTouchingBorder = 0;
		for ( LabelRegion labelRegion : labelRegions )
		{
			Cursor cursor = labelRegion.inside().cursor();

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

		IJ.log( "Removed " + numRegionsTouchingBorder + " of " + size + " regions, because of image border contact." );
		return size - numRegionsTouchingBorder;
	}

	public static RandomAccessibleInterval< BitType > openFast( RandomAccessibleInterval< BitType > dnaAlignedSpindleMask )
	{
		final RandomAccessibleInterval< BitType > downSampled = createNearestNeighborResampledArrayImg( dnaAlignedSpindleMask, new double[]{ 0.2, 0.2, 0.2 } );

		final RandomAccessibleInterval< BitType > opened = open( downSampled, 2 );

		final RandomAccessibleInterval< BitType > openedUpSampled = createNearestNeighborResampledArrayImg( opened, new double[]{ 1 / 0.2, 1 / 0.2, 1 / 0.2 } );

		return openedUpSampled;
	}

	public static RandomAccessibleInterval< BitType > open(
			RandomAccessibleInterval< BitType > mask,
			int radius )
	{
		if ( radius <= 0 ) return mask;

		// TODO: Bug(?!) in imglib2 makes enlargement necessary,
		//  otherwise one gets weird results at boundaries

		RandomAccessibleInterval< BitType > morphed =
				ArrayImgs.bits( Intervals.dimensionsAsLongArray( mask ) );

		morphed = Views.translate( morphed, Intervals.minAsLongArray( mask ) );

		final RandomAccessibleInterval< BitType > enlargedMask =
				Utils.getEnlargedRai( mask, radius );
		final RandomAccessibleInterval< BitType > enlargedMorphed =
				Utils.getEnlargedRai( morphed, radius );

		Shape shape = new HyperSphereShape( radius );

		Opening.open(
				Views.extendZero( enlargedMask ),
				Views.iterable( enlargedMorphed ),
				shape,
				4 );

		return Views.interval( enlargedMorphed, mask );
	}

	public static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< T > createNearestNeighborResampledArrayImg(
			RandomAccessibleInterval< T > input,
			double[] scalingFactors )
	{
		// Convert to RealRandomAccessible such that we can obtain values at (infinite) non-integer coordinates
		RealRandomAccessible< T > rra =
				Views.interpolate( Views.extendBorder( input ),
						new NearestNeighborInterpolatorFactory<>() );

		// Change scale such that we can sample from integer coordinates (for raster function below)
		Scale scale = new Scale( scalingFactors );
		RealRandomAccessible< T > rescaledRRA  = RealViews.transform( rra, scale );

		// Create view sampled at integer coordinates
		final RandomAccessible< T > rastered = Views.raster( rescaledRRA );

		// Put an interval to make it a finite "normal" image again
		final RandomAccessibleInterval< T > finiteRastered =
				Views.interval( rastered, createTransformedInterval( input, scale ) );

		// Convert from View to a "conventional" image in RAM
		// - Above code would also run on, e.g. 8 TB image, within ms
		// - Now, we really force it to create the image
		// (we actually might now have to, depends...)
		final RandomAccessibleInterval< T > output = copyAsArrayImg( finiteRastered );

		return output;
	}

	public static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< T > copyAsArrayImg( RandomAccessibleInterval< T > orig )
	{
		final RandomAccessibleInterval< T > copy =
				Views.translate(
						new ArrayImgFactory( Util.getTypeFromInterval( orig ) ).create( orig ),
						Intervals.minAsLongArray( orig ) );

		LoopBuilder.setImages( copy, orig ).forEachPixel( Type::set );

		return copy;
	}


	public static < T extends NumericType< T > >
	FinalInterval createTransformedInterval( RandomAccessibleInterval< T > rai, InvertibleRealTransform transform )
	{
		final FinalInterval transformedInterval;

		if ( transform instanceof AffineTransform3D )
		{
			FinalRealInterval transformedRealInterval = ( ( AffineTransform3D ) transform ).estimateBounds( rai );
			transformedInterval = asIntegerInterval( transformedRealInterval );
		}
		else if ( transform instanceof Scale )
		{
			transformedInterval = createScaledInterval( rai, ( Scale ) transform );
		}
		else
		{
			transformedInterval = null;
		}

		return transformedInterval;
	}

	public static < R extends RealType< R > & NativeType< R > >
	RandomAccessibleInterval< R > dilate(
			RandomAccessibleInterval< R > image,
			int radius )
	{
		final RandomAccessibleInterval< R > morphed = Utils.createEmptyCopy( image );

		if ( radius > 0 )
		{
			Shape shape = new HyperSphereShape( radius );
			Dilation.dilate( Views.extendBorder( image ), Views.iterable( morphed ), shape, 1 );
		}

		return morphed;
	}

	public static AffineTransform3D createShortestAxisAlignmentTransform( double[] center, double[] array )
	{
		AffineTransform3D translation = new AffineTransform3D();
		translation.translate( center );
		translation = translation.inverse();

		final double[] zAxis = new double[]{ 0, 0, 1 };
		final double[] shortestAxis = array;
		AffineTransform3D rotation = getRotationTransform3D( zAxis, shortestAxis );

		AffineTransform3D combinedTransform = translation.preConcatenate( rotation );

		return combinedTransform;
	}

	/**
	 * Rotation transform which rotates {@code axis} onto {@code targetAxis}
	 *
	 * @param normalisedTargetAxis
	 * @param normalisedAxis
	 * @return rotationTransform
	 */
	public static AffineTransform3D getRotationTransform3D(
			double[] normalisedTargetAxis,
			double[] normalisedAxis )
	{

		// Note that for the dot-product the order of the vectors does not matter.
		double rotationAngle = Math.acos( LinAlgHelpers.dot( normalisedAxis, normalisedTargetAxis ) );

		if ( rotationAngle == 0.0 ) return new AffineTransform3D();

		// Note that here, for the cross-product, the order of the vectors is important!
		double[] rotationAxis = new double[3];
		LinAlgHelpers.cross( normalisedAxis, normalisedTargetAxis, rotationAxis );

		if ( LinAlgHelpers.length( rotationAxis ) == 0.0 )
		{
			// Since the rotation angle is not zero (see above), the vectors are anti-parallel.
			// This means that the cross product does not work for finding a perpendicular vector.
			// It also means we need to rotate 180 degrees around any axis that
			// is perpendicular to any of the two vectors.
			// That means that the dot-product of the rotation axis and any
			// of the two vectors should be zero:
			// u * x + v * y + w * z != 0

			rotationAxis = VectorUtils.getPerpendicularVector( normalisedAxis );

		}

		LinAlgHelpers.normalize( rotationAxis );

		final double[] q = new double[ 4 ];
		LinAlgHelpers.quaternionFromAngleAxis( rotationAxis, rotationAngle, q );

		final double[][] m = new double[ 3 ][ 4 ];
		LinAlgHelpers.quaternionToR( q, m );

		AffineTransform3D rotation = new AffineTransform3D();
		rotation.set( m );

		return rotation;
	}

	public static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< BitType > createMask(
			RandomAccessibleInterval< T > rai,
			double threshold,
			OpService opService )
	{
		RandomAccessibleInterval< BitType > mask
				= Converters.convert( rai, ( i, o ) ->
				o.set( i.getRealDouble() > threshold ), new BitType() );

		// "Bug" in Ops requires a Views.zeroMin().
		mask = opService.morphology().fillHoles( Views.zeroMin( mask ) );

		mask = getWithAdjustedOrigin( rai, mask );

		return mask;
	}

	public static < T extends Type< T > >
	RandomAccessibleInterval< T > getWithAdjustedOrigin(
			Interval interval,
			RandomAccessibleInterval< T > rai )
	{
		long[] offset = new long[ interval.numDimensions() ];
		interval.min( offset );
		RandomAccessibleInterval translated = Views.translate( rai, offset );
		return translated;
	}

	public static double thresholdOtsu( ArrayList< Double > values )
	{
		ArrayImg< DoubleType, DoubleArray > doubles = ArrayImgs.doubles( values.size() );
		ArrayCursor< DoubleType > cursor = doubles.cursor();
		int i = 0;
		while ( cursor.hasNext() )
		{
			cursor.next().set( values.get( i++ ) );
		}

		return thresholdOtsu( doubles );
	}

	public static < T extends RealType< T > >
	double thresholdOtsu( RandomAccessibleInterval< T > rai )
	{
		final Histogram1d< T > histogram =
				histogram( rai, 256 );

		final T type = Views.iterable( rai ).firstElement().createVariable();
		final ComputeOtsuThreshold< T > threshold = new ComputeOtsuThreshold<>();
		final long bin = threshold.computeBin( histogram );
		histogram.getCenterValue( bin, type );

		return type.getRealDouble();
	}

	public static < T extends RealType< T > > Histogram1d< T >
	histogram( RandomAccessibleInterval< T > rai, int numBins )
	{
		final Pair< Double, Double > minMaxValues = getMinMaxValues( rai );

		final Real1dBinMapper< T > tReal1dBinMapper =
				new Real1dBinMapper<>( minMaxValues.getA(),
						minMaxValues.getB(),
						numBins,
						false );

		final Histogram1d<T> histogram1d = new Histogram1d<>( tReal1dBinMapper );

		histogram1d.countData( Views.iterable(  rai ) );

		return histogram1d;
	}

	public static < T extends RealType< T > >
	Pair< Double, Double > getMinMaxValues( RandomAccessibleInterval< T > rai )
	{
		Cursor< T > cursor = Views.iterable( rai ).localizingCursor();

		double maxValue = - Double.MAX_VALUE;
		double minValue = Double.MAX_VALUE;

		while ( cursor.hasNext() )
		{
			final double value = cursor.next().getRealDouble();

			if ( value > maxValue ) maxValue = value;
			if ( value < minValue ) minValue = value;
		}

		return new ValuePair<>( minValue, maxValue );
	}
}
