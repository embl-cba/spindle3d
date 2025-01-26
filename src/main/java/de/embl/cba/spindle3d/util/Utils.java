package de.embl.cba.spindle3d.util;

import ij.ImagePlus;
import ij.measure.Calibration;
import loci.plugins.in.ImagePlusReader;
import loci.plugins.in.ImportProcess;
import loci.plugins.in.ImporterOptions;
import net.imglib2.*;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.converter.Converters;
import net.imglib2.img.AbstractImg;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.outofbounds.OutOfBounds;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelRegion;
import net.imglib2.roi.labeling.LabelRegions;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.util.*;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import javax.swing.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static de.embl.cba.spindle3d.Spindle3DUtils.createTransformedInterval;
import static de.embl.cba.spindle3d.Spindle3DUtils.getWithAdjustedOrigin;

public class Utils
{

	public enum BorderExtension
	{
		ExtendZero,
		ExtendBorder,
		ExtendMirror
	}

	public static long[] asRoundedLongs( double[] doubles )
	{
		final long[] longs = new long[ doubles.length ];

		for ( int i = 0; i < doubles.length; ++i )
		{
			longs[ i ] = Math.round( doubles[ i ] );
		}

		return longs;
	}

	public static long[] asLongs( double[] doubles )
	{
		final long[] longs = new long[ doubles.length ];

		for ( int i = 0; i < doubles.length; ++i )
		{
			longs[ i ] = (long) doubles[ i ];
		}

		return longs;
	}

	public static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< BitType > createMask(
			RandomAccessibleInterval< T > rai,
			double threshold )
	{
		RandomAccessibleInterval< BitType > mask =
				Converters.convert( rai, ( i, o )
						-> o.set( i.getRealDouble() > threshold ? true : false ), new BitType() );

		return Utils.copyAsArrayImg( mask );
	}

	public static Set< LabelRegion< Integer > > getCentralRegions(
			ImgLabeling< Integer, IntType > labeling,
			double[] center,
			long radius )
	{
		final Set< Integer > centralLabels = getCentralLabels( labeling, center, radius );

		final LabelRegions< Integer > regions = new LabelRegions<>( labeling );

		final HashSet< LabelRegion< Integer > > centralRegions = new HashSet< >();

		for ( int label : centralLabels )
		{
			centralRegions.add( regions.getLabelRegion( label ) );
		}

		return centralRegions;
	}

	public static Set< Integer > getCentralLabels(
			ImgLabeling< Integer, IntType > labeling,
			double[] center,
			long maxCenterDistance )
	{

		if ( labeling.getMapping().numSets() == 0 )
			return new HashSet<>(  );

		final HyperSphereShape sphere = new HyperSphereShape( maxCenterDistance );

		final RandomAccessible< Neighborhood< IntType > > nra =
				sphere.neighborhoodsRandomAccessible( Views.extendZero( labeling.getIndexImg() ) );

		final RandomAccess< Neighborhood< IntType > > neighborhoodRandomAccess =
				nra.randomAccess();

		neighborhoodRandomAccess.setPosition( Utils.asLongs( center ) );

		final Cursor< IntType > cursor = neighborhoodRandomAccess.get().cursor();

		final Set< Integer > centralIndices = new HashSet<>();
		while( cursor.hasNext() )
		{
			if ( cursor.next().get() != 0 )
			{
				centralIndices.add( cursor.get().getInteger() );
			}
		}

		final Set< Integer > centralLabels = new HashSet<>();
		for ( int index : centralIndices )
		{
			final ArrayList< Integer > labels =
					new ArrayList<>( labeling.getMapping().labelsAtIndex( index ) );
			centralLabels.add( labels.get( 0 ) );
		}

		return centralLabels;
	}

	public static RandomAccessibleInterval< BitType > asMask( LabelRegion labelRegion )
	{
		RandomAccessibleInterval< BitType > rai = ArrayImgs.bits( Intervals.dimensionsAsLongArray( labelRegion ) );
		rai = Transforms.getWithAdjustedOrigin( labelRegion, rai  );
		final RandomAccess< BitType > randomAccess = rai.randomAccess();

		final Cursor cursor = labelRegion.inside().cursor();

		while ( cursor.hasNext() )
		{
			cursor.fwd();
			randomAccess.setPosition( cursor );
			randomAccess.get().set( true );
		}

		return rai;
	}


	public static RandomAccessibleInterval< BitType > asMask(
			Set< LabelRegion< Integer > > regions,
			long[] dimensions,
			long[] offset )
	{
		RandomAccessibleInterval< BitType > regionsMask = ArrayImgs.bits( dimensions );
		regionsMask = Views.translate( regionsMask, offset );

		final RandomAccess< BitType > maskAccess = regionsMask.randomAccess();

		for ( LabelRegion region : regions )
		{
			final Cursor< Void > regionCursor = region.cursor();
			while ( regionCursor.hasNext() )
			{
				regionCursor.fwd();
				maskAccess.setPosition( regionCursor );
				maskAccess.get().set( true );
			}
		}

		return regionsMask;
	}

	public static < T extends RealType< T > & NativeType< T > >
	CoordinatesAndValues computeRadialProfile(
			RandomAccessibleInterval< T > image,
			double[] center,
			double spacing,
			double maxDistanceInMicrometer )
	{
		final Cursor< T > cursor = Views.iterable( image ).cursor();
		final int numDimensions = image.numDimensions();

		final double[] position = new double[ numDimensions ];

		int maxBin = ( int ) ( maxDistanceInMicrometer / spacing );
		double[] counts = new double[ maxBin ];
		double[] values = new double[ maxBin ];

		while ( cursor.hasNext() )
		{
			cursor.localize( position );
			final double distance = LinAlgHelpers.distance( center, position ) * spacing;
			final double value = cursor.next().getRealDouble();

			final int bin = ( int ) ( distance / spacing );
			if ( bin < maxBin )
			{
				counts[ bin ] += 1;
				values[ bin ] += value;
			}
		}

		final CoordinatesAndValues radialProfileOld = new CoordinatesAndValues();
		final CoordinateToValue radialProfile = new CoordinateToValue();
		for ( int bin = 0; bin < maxBin; bin++ )
		{
			radialProfileOld.values.add( values[ bin ] / counts[ bin ] );
			radialProfileOld.coordinates.add( bin * spacing );
			radialProfile.put( bin * spacing, values[ bin ] / counts[ bin ] );
		}

		return radialProfileOld;
	}

	public static double[] copy( double[] values )
	{
		final double[] copy = new double[ values.length ];
		for ( int i = 0; i < values.length; i++ ) copy[ i ] = values[ i ];
		return copy;
	}

	public static < R extends RealType< R > & NativeType< R > >
	RandomAccessibleInterval< BitType > createEmptyMask( RandomAccessibleInterval< R > image )
	{
		RandomAccessibleInterval< BitType > mask =
				new ArrayImgFactory( new BitType(  ) ).create( image );
		mask = Views.translate( mask, Intervals.minAsLongArray( image ) );
		return mask;
	}

	public static < T extends RealType< T > & NativeType< T > >
	CoordinatesAndValues computeMaximumIntensitiesAlongAxis(
			RandomAccessibleInterval< T > rai, double maxAxisDist, int axis, double calibration )
	{
		final CoordinatesAndValues coordinatesAndValues = new CoordinatesAndValues();

		for ( long coordinate = rai.min( axis ); coordinate <= rai.max( axis ); ++coordinate )
		{
			final IntervalView< T > intensitySlice = Views.hyperSlice( rai, axis, coordinate );
			coordinatesAndValues.coordinates.add( (double) coordinate * calibration );
			coordinatesAndValues.values.add( computeMaximum( intensitySlice, maxAxisDist ) );
		}

		return coordinatesAndValues;
	}

	public static IndexAndValue maximumIndexAndValue( CoordinatesAndValues coordinatesAndValues )
	{
		final int n = coordinatesAndValues.coordinates.size();

		final IndexAndValue indexAndValue = new IndexAndValue();
		indexAndValue.value = - Double.MAX_VALUE;

		for ( int i = 0; i < n; i++ )
		{
			if ( coordinatesAndValues.values.get( i ) > indexAndValue.value )
			{
				indexAndValue.value = coordinatesAndValues.values.get( i );
				indexAndValue.index = i;
			}
		}

		return indexAndValue;
	}

	public static < R extends RealType< R > & NativeType< R > >
	void onlyKeepLargestRegion( RandomAccessibleInterval< R > mask,
								ConnectedComponents.StructuringElement structuringElement )
	{
		final ArrayList< RegionAndSize > sortedRegions =
				Regions.getSizeSortedRegions(
						mask,
						structuringElement );
		Utils.setValues( mask, 0 );
		Regions.drawRegion( mask, sortedRegions.get( 0 ).getRegion(), 1.0 );
	}

	private static < R extends RealType< R > & NativeType< R > >
	void drawRegion( RandomAccessibleInterval< R > img,
					 LabelRegion labelRegion,
					 double value)
	{
		final Cursor< Void > regionCursor = labelRegion.inside().cursor();
		final RandomAccess< R > access = img.randomAccess();
		while ( regionCursor.hasNext() )
		{
			regionCursor.fwd();
			access.setPosition( regionCursor );
			access.get().setReal( value );
		}
	}

	public static ArrayList< RealPoint > origin()
	{
		final ArrayList< RealPoint > origin = new ArrayList<>();
		origin.add( new RealPoint( new double[]{ 0, 0, 0 } ) );
		return origin;
	}


	public static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< T > createBlurredRai(
			RandomAccessibleInterval< T > rai,
			double sigma,
			double scaling )
	{
		ImgFactory< T > imgFactory = new ArrayImgFactory( rai.randomAccess().get()  );

		RandomAccessibleInterval< T > blurred = imgFactory.create( Intervals.dimensionsAsLongArray( rai ) );

		blurred = Views.translate( blurred, Intervals.minAsLongArray( rai ) );

		Gauss3.gauss( sigma / scaling, Views.extendBorder( rai ), blurred ) ;

		return blurred;
	}

	public static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< T > createGaussFilteredArrayImg(
			RandomAccessibleInterval< T > rai,
			double[] sigmas )
	{
		ImgFactory< T > imgFactory = new ArrayImgFactory( rai.randomAccess().get()  );

		RandomAccessibleInterval< T > blurred = imgFactory.create( Intervals.dimensionsAsLongArray( rai ) );

		blurred = Views.translate( blurred, Intervals.minAsLongArray( rai ) );

		Gauss3.gauss( sigmas, Views.extendBorder( rai ), blurred ) ;

		return blurred;
	}


	public static  < T extends RealType< T > & NativeType< T > >
	void applyMask(
			RandomAccessibleInterval< T > rai,
			RandomAccessibleInterval< BitType > mask )
	{
		final Cursor< T > cursor = Views.iterable( rai ).cursor();
		final OutOfBounds< BitType > maskAccess =
				Views.extendZero( mask ).randomAccess();

		while ( cursor.hasNext() )
		{
			cursor.fwd();
			maskAccess.setPosition( cursor );
			if ( ! maskAccess.get().get() )
				cursor.get().setZero();
		}
	}
	
	public static < T extends RealType< T > & NativeType< T > >
	CoordinatesAndValues computeAverageIntensitiesAlongAxis(
			RandomAccessibleInterval< T > rai,
			double maxAxisDist,
			int axis,
			double axisMin, // calibrated
			double axisMax,
			double calibration )
	{
		final CoordinatesAndValues coordinatesAndValues = new CoordinatesAndValues();

		final double xMin = axisMin / calibration;
		final double xMax = axisMax / calibration;
		for ( long x = ( long ) xMin; x <= ( long ) xMax; ++x )
		{
			final IntervalView< T > intensitySlice = Views.hyperSlice( rai, axis, x );
			coordinatesAndValues.coordinates.add( (double) x * calibration );
			coordinatesAndValues.values.add( computeAverage( intensitySlice, maxAxisDist, calibration ) );
		}

		return coordinatesAndValues;
	}

	public static < T extends RealType< T > & NativeType< T > >
	CoordinatesAndValues computeAverageIntensitiesAlongAxis(
			RandomAccessibleInterval< T > rai,
			double maxAxisDist,
			int axis,
			double calibration )
	{
		return computeAverageIntensitiesAlongAxis( rai, maxAxisDist, axis, rai.min( axis ) * calibration, rai.max( axis ) * calibration, calibration );
	}


	public static CoordinatesAndValues derivative( CoordinatesAndValues coordinatesAndValues, int di )
	{
		final CoordinatesAndValues derivative = new CoordinatesAndValues();

		for ( int i = di / 2 + 1; i < coordinatesAndValues.values.size() - ( di / 2 + 1 ); ++i )
		{

			final int center = i;
			final int right = i + di / 2;
			final int left = i - di / 2;

			derivative.values.add(
					coordinatesAndValues.values.get( right )
							- coordinatesAndValues.values.get( left ) );

			derivative.coordinates.add( coordinatesAndValues.coordinates.get( center ) );
		}

		return derivative;
	}


	public static ArrayList< CoordinateAndValue >
	leftMaxAndRightMinLoc( CoordinatesAndValues coordinatesAndValues )
	{
		Double[] rangeMinMax = new Double[ 2 ];

		final ArrayList< CoordinateAndValue > extrema = new ArrayList<>();

		// left
		rangeMinMax[ 0 ] = - Double.MAX_VALUE;
		rangeMinMax[ 1 ] = 0.0;
		extrema.add( maximum( coordinatesAndValues, rangeMinMax ) );

		// right
		rangeMinMax[ 0 ] = 0.0;
		rangeMinMax[ 1 ] = Double.MAX_VALUE;
		extrema.add( minimum( coordinatesAndValues, rangeMinMax ) );

		return extrema;
	}

	public static CoordinateAndValue maximum( CoordinatesAndValues coordinatesAndValues )
	{
		return maximum( coordinatesAndValues, null );
	}

	public static CoordinateAndValue maximum(
			CoordinatesAndValues coordinatesAndValues,
			Double[] coordinateRangeMinMax )
	{
		final ArrayList< Double > coordinates = coordinatesAndValues.coordinates;
		final ArrayList< Double > values = coordinatesAndValues.values;
		final int n = values.size();

		Double max = - Double.MAX_VALUE;
		Double maxLoc = coordinates.get( 0 );

		for ( int i = 0; i < n; ++i )
		{
			if ( coordinateRangeMinMax != null )
			{
				if ( coordinates.get( i ) < coordinateRangeMinMax[ 0 ] ) continue;
				if ( coordinates.get( i ) > coordinateRangeMinMax[ 1 ] ) continue;
			}

			if ( values.get( i ) > max )
			{
				max = values.get( i );
				maxLoc = coordinates.get( i );
			}
		}

		final CoordinateAndValue coordinateAndValue = new CoordinateAndValue();
		coordinateAndValue.value = max;
		coordinateAndValue.coordinate = maxLoc;

		return coordinateAndValue;
	}

	public static CoordinateAndValue minimum( CoordinatesAndValues coordinatesAndValues )
	{
		return minimum( coordinatesAndValues, null );
	}

	public static CoordinateAndValue minimum(
			CoordinatesAndValues coordinatesAndValues, Double[] coordinateRangeMinMax )
	{
		final int n = coordinatesAndValues.coordinates.size();
		final ArrayList< Double > coordinates = coordinatesAndValues.coordinates;
		final ArrayList< Double > values = coordinatesAndValues.values;

		final IndexAndValue minLocIndexAndValue = new IndexAndValue();
		minLocIndexAndValue.value = Double.MAX_VALUE;

		for ( int i = 0; i < n; i++ )
		{

			if ( coordinateRangeMinMax != null )
			{
				if ( coordinates.get( i ) < coordinateRangeMinMax[ 0 ] ) continue;
				if ( coordinates.get( i ) > coordinateRangeMinMax[ 1 ] ) continue;
			}

			if ( values.get( i ) < minLocIndexAndValue.value )
			{
				minLocIndexAndValue.value = coordinatesAndValues.values.get( i );
				minLocIndexAndValue.index = i;
			}
		}

		final CoordinateAndValue coordinateAndValue = new CoordinateAndValue();
		coordinateAndValue.coordinate =
				coordinatesAndValues.coordinates.get( minLocIndexAndValue.index );
		coordinateAndValue.value = minLocIndexAndValue.value;

		return coordinateAndValue;
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

	public static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< T > createRescaledArrayImg(
			RandomAccessibleInterval< T > input,
			double[] scalingFactors )
	{
		assert scalingFactors.length == input.numDimensions();

		/**
		 * - In principle, writing a function that computes weighted averages
		 *   of an appropriate number of neighboring (not only nearest) pixels
		 *   around each requested (real) position in the new image appears to me
		 *   the most straight-forward way of rescaling.
		 * - However, in practice, blurring and subsequent re-sampling seems to be more commonly done,
		 *   maybe for implementation efficiency?
		 * - http://imagej.1557.x6.nabble.com/downsampling-methods-td3690444.html
		 * - https://github.com/axtimwalde/mpicbg/blob/050bc9110a186394ea15190fd326b3e32829e018/mpicbg/src/main/java/mpicbg/ij/util/Filter.java#L424
		 * - https://imagej.net/Downsample
		 */

		/*
		 * Blur image
		 */

		final RandomAccessibleInterval< T > blurred =
				createOptimallyBlurredArrayImg( input, scalingFactors );

		/*
		 * Sample values from blurred image
		 */

		final RandomAccessibleInterval< T > resampled =
				createResampledArrayImg( blurred, scalingFactors );

		return resampled;
	}

	public static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< T > createRescaledCellImg(
			RandomAccessibleInterval< T > input,
			double[] scalingFactors )
	{
		assert scalingFactors.length == input.numDimensions();

		/**
		 * - In principle, writing a function that computes weighted averages
		 *   of an appropriate number of neighboring (not only nearest) pixels
		 *   around each requested (real) position in the new image appears to me
		 *   the most straight-forward way of rescaling.
		 * - However, in practice, blurring and subsequent re-sampling seems to be more commonly done,
		 *   maybe for implementation efficiency?
		 * - http://imagej.1557.x6.nabble.com/downsampling-methods-td3690444.html
		 * - https://github.com/axtimwalde/mpicbg/blob/050bc9110a186394ea15190fd326b3e32829e018/mpicbg/src/main/java/mpicbg/ij/util/Filter.java#L424
		 * - https://imagej.net/Downsample
		 */

		/*
		 * Blur image
		 */

		final RandomAccessibleInterval< T > blurred =
				createOptimallyBlurredCellImg( input, scalingFactors );

		/*
		 * Sample values from blurred image
		 */

		final RandomAccessibleInterval< T > resampled =
				createResampledArrayImg( blurred, scalingFactors );

		return resampled;
	}

	public static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< T > createResampledArrayImg(
			RandomAccessibleInterval< T > input,
			double[] scalingFactors )
	{
		// Convert to RealRandomAccessible such that we can obtain values at (infinite) non-integer coordinates
		RealRandomAccessible< T > rra =
				Views.interpolate( Views.extendBorder( input ),
						new ClampingNLinearInterpolatorFactory<>() );

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

	private static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< T > createOptimallyBlurredArrayImg(
			RandomAccessibleInterval< T > input,
			double[] scalingFactors )
	{
		/**
		 * - https://en.wikipedia.org/wiki/Decimation_(signal_processing)
		 * - Optimal blurring is 0.5 / M, where M is the downsampling factor
		 */

		final double[] sigmas = new double[input.numDimensions() ];

		for ( int d = 0; d < input.numDimensions(); ++d )
			sigmas[ d ] = 0.5 / scalingFactors[ d ];

		// allocate output image
		RandomAccessibleInterval< T > output = createEmptyArrayImg( input );

		// blur input image and write into output image
		Gauss3.gauss( sigmas, Views.extendBorder( input ), output ) ;

		return output;
	}

	public static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< T > createEmptyArrayImg( RandomAccessibleInterval< T > rai )
	{
		RandomAccessibleInterval< T > newImage = new ArrayImgFactory( rai.randomAccess().get() ).create( rai );
		newImage = getWithAdjustedOrigin( rai, newImage );
		return newImage;
	}

	private static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< T > createOptimallyBlurredCellImg(
			RandomAccessibleInterval< T > input,
			double[] scalingFactors )
	{
		/**
		 * - https://en.wikipedia.org/wiki/Decimation_(signal_processing)
		 * - Optimal blurring is 0.5 / M, where M is the downsampling factor
		 */

		final double[] sigmas = new double[input.numDimensions() ];

		for ( int d = 0; d < input.numDimensions(); ++d )
			sigmas[ d ] = 0.5 / scalingFactors[ d ];

		// allocate output image
		RandomAccessibleInterval< T > output = createEmptyCellImg( input );

		// blur input image and write into output image
		Gauss3.gauss( sigmas, Views.extendBorder( input ), output ) ;

		return output;
	}

	public static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< T > createEmptyCellImg( RandomAccessibleInterval< T > volume )
	{
		final int dimensionX = ( int ) volume.dimension( 0 );
		final int dimensionY = ( int ) volume.dimension( 1 );
		final int dimensionZ = ( int ) volume.dimension( 2 );

		int nz = dimensionZ;
		if ( AbstractImg.numElements( Intervals.dimensionsAsLongArray( volume ) ) >  Integer.MAX_VALUE - 1 )
			nz  = ( Integer.MAX_VALUE / 2 ) / ( dimensionX * dimensionY );

		final int[] cellSize = {
				dimensionX,
				dimensionY,
				nz };

		RandomAccessibleInterval< T > newImage = new CellImgFactory<>(
				volume.randomAccess().get(),
				cellSize ).create( volume );

		newImage = Transforms.getWithAdjustedOrigin( volume, newImage );
		return newImage;
	}

	public static double[] getScalingFactors( double[] calibration, double targetResolution )
	{

		double[] downScaling = new double[ calibration.length ];

		for ( int d = 0; d < calibration.length; ++d )
		{
			downScaling[ d ] = calibration[ d ] / targetResolution;
		}

		return downScaling;
	}

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

	public static FinalInterval asIntegerInterval( FinalRealInterval realInterval )
	{
		double[] realMin = new double[ 3 ];
		double[] realMax = new double[ 3 ];
		realInterval.realMin( realMin );
		realInterval.realMax( realMax );

		long[] min = new long[ 3 ];
		long[] max = new long[ 3 ];

		for ( int d = 0; d < min.length; ++d )
		{
			min[ d ] = (long) realMin[ d ];
			max[ d ] = (long) realMax[ d ];
		}

		return new FinalInterval( min, max );
	}


	public static < R extends RealType< R > & NativeType< R > >
	double computeCoefficientOfVariation(
			RandomAccessibleInterval< R > intensities,
			RandomAccessibleInterval< BitType > mask,
			Double meanOffset )
	{
		final ArrayList< Double > intensitiesWithMask =
				getValuesWithinMaskAsList( intensities, mask );

		final double mean = mean( intensitiesWithMask );
		final double sdev = sdev( intensitiesWithMask, mean );

		double cov = sdev / ( mean - meanOffset );

		return cov;
	}

	public static double mean( List<Double> values ){
		double sum = sum( values );
		double mean = 0;
		mean = sum / ( values.size() * 1.0 );
		return mean;
	}

	public static double sdev( List<Double> values, Double mean ){
		double sdev = 0;
		for( double v : values )
			sdev += ( v - mean ) * ( v - mean );
		sdev /= ( values.size() * 1.0 );
		sdev = Math.sqrt( sdev );
		return sdev;
	}

	public static < T extends RealType< T > & NativeType< T > >
	double computeAverage( final RandomAccessibleInterval< T > rai,
						   final RandomAccessibleInterval< BitType > mask )
	{
		final Cursor< BitType > cursor = Views.iterable( mask ).cursor();
		final RandomAccess< T > randomAccess = rai.randomAccess();

		randomAccess.setPosition( cursor );

		double average = 0;
		long n = 0;

		while ( cursor.hasNext() )
		{
			if ( cursor.next().get() )
			{
				randomAccess.setPosition( cursor );
				average += randomAccess.get().getRealDouble();
				++n;
			}
		}

		average /= n;

		return average;
	}

	public static double mad( List<Double> values, Double median )
	{
		final ArrayList< Double > absoluteDeviations = new ArrayList<>();

		for ( Double value : values )
		{
			absoluteDeviations.add( Math.abs( value - median ) );
		}

		return median( absoluteDeviations );
	}


	public static < T extends NumericType< T > & NativeType< T > >
	RandomAccessibleInterval createTransformedView( RandomAccessibleInterval< T > rai,
													InvertibleRealTransform combinedTransform,
													InterpolatorFactory interpolatorFactory)
	{
		final RandomAccessible transformedRA = createTransformedRaView( rai, combinedTransform, interpolatorFactory );
		final FinalInterval transformedInterval = createBoundingIntervalAfterTransformation( rai, combinedTransform );
		final RandomAccessibleInterval< T > transformedIntervalView = Views.interval( transformedRA, transformedInterval );

		return transformedIntervalView;
	}

	public static FinalInterval createBoundingIntervalAfterTransformation( Interval interval, InvertibleRealTransform transform )
	{
		List< long[ ] > corners = Corners.corners( interval );

		long[] boundingMin = new long[ interval.numDimensions() ];
		long[] boundingMax = new long[ interval.numDimensions() ];
		Arrays.fill( boundingMin, Long.MAX_VALUE );
		Arrays.fill( boundingMax, Long.MIN_VALUE );

		for ( long[] corner : corners )
		{
			double[] transformedCorner = transformedCorner( transform, corner );

			adjustBoundingRange( boundingMin, boundingMax, transformedCorner );
		}

		return new FinalInterval( boundingMin, boundingMax );
	}

	private static void adjustBoundingRange( long[] min, long[] max, double[] transformedCorner )
	{
		for ( int d = 0; d < transformedCorner.length; ++d )
		{
			if ( transformedCorner[ d ] > max[ d ] )
			{
				max[ d ] = (long) transformedCorner[ d ];
			}

			if ( transformedCorner[ d ] < min[ d ] )
			{
				min[ d ] = (long) transformedCorner[ d ];
			}
		}
	}

	private static double[] transformedCorner( InvertibleRealTransform transform, long[] corner )
	{
		double[] cornerAsDouble = Arrays.stream( corner ).mapToDouble( x -> x ).toArray();
		double[] transformedCorner = new double[ corner.length ];
		transform.apply( cornerAsDouble, transformedCorner );
		return transformedCorner;
	}


	public static void openURI( String uri )
	{
		try
		{
			java.awt.Desktop.getDesktop().browse( new URI( uri ));
		} catch ( IOException e )
		{
			e.printStackTrace();
		} catch ( URISyntaxException e )
		{
			e.printStackTrace();
		}
	}

	public static ImagePlus openWithBioFormats( File file )
	{
		return openWithBioFormats( file.getAbsolutePath() );
	}

	public static ImagePlus openWithBioFormats( String path )
	{
		try
		{
			ImporterOptions opts = new ImporterOptions();
			opts.setId( path );
			opts.setVirtual( true );
			ImportProcess process = new ImportProcess( opts );
			process.execute();
			ImagePlusReader impReader = new ImagePlusReader( process );
			ImagePlus[] imps = impReader.openImagePlus();
			return imps[ 0 ];
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			return null;
		}
	}

	public static < T extends NumericType< T > & NativeType< T > >
	RandomAccessibleInterval createTransformedView( RandomAccessibleInterval< T > rai,
													InvertibleRealTransform combinedTransform,
													InterpolatorFactory interpolatorFactory,
													BorderExtension borderExtension)
	{
		final RandomAccessible transformedRA = createTransformedRaView( rai, combinedTransform, interpolatorFactory, borderExtension );
		final FinalInterval transformedInterval = createBoundingIntervalAfterTransformation( rai, combinedTransform );
		final RandomAccessibleInterval< T > transformedIntervalView = Views.interval( transformedRA, transformedInterval );

		return transformedIntervalView;
	}

	public static < T extends NumericType< T > >
	RandomAccessible createTransformedRaView(
			RandomAccessibleInterval< T > rai,
			InvertibleRealTransform combinedTransform,
			InterpolatorFactory interpolatorFactory,
			BorderExtension borderExtension )
	{
		ExtendedRandomAccessibleInterval< T, RandomAccessibleInterval< T > > source = createExtendedRai( rai, borderExtension );
		RealRandomAccessible rra = Views.interpolate( source, interpolatorFactory );
		rra = RealViews.transform( rra, combinedTransform );
		return Views.raster( rra );
	}

	private static < T extends NumericType< T > > ExtendedRandomAccessibleInterval< T, RandomAccessibleInterval< T > > createExtendedRai( RandomAccessibleInterval< T > rai, BorderExtension borderExtension )
	{
		switch ( borderExtension )
		{
			case ExtendZero:
				return Views.extendZero( rai );
			case ExtendBorder:
				return Views.extendBorder( rai );
			case ExtendMirror:
				return Views.extendMirrorDouble( rai );
			default:
				return Views.extendZero( rai );
		}
	}

	public static JTable createJTableFromStringList( List< String > strings, String delim )
	{
		delim = autoDelim( delim, strings );

		StringTokenizer st = new StringTokenizer( strings.get( 0 ), delim );

		List< String > colNames = new ArrayList<>();

		while ( st.hasMoreTokens() )
			colNames.add( st.nextToken() );

		/**
		 * Init model and columns
		 */

		ColumnClassAwareTableModel model = new ColumnClassAwareTableModel();

		for ( String colName : colNames )
			model.addColumn( colName );

		int numCols = colNames.size();

		/**
		 * Add tablerow entries
		 */

		for ( int iString = 1; iString < strings.size(); ++iString )
		{
			model.addRow( new Object[ numCols ] );

			st = new StringTokenizer( strings.get( iString ), delim );

			for ( int iCol = 0; iCol < numCols; iCol++ )
			{
				String stringValue = st.nextToken();

				try
				{
					final Double numericValue = Utils.parseDouble( stringValue );
					model.setValueAt( numericValue, iString - 1, iCol );
				} catch ( Exception e )
				{
					model.setValueAt( stringValue, iString - 1, iCol );
				}
			}

		}

		model.refreshColumnClassesFromObjectColumns();

		return new JTable( model );
	}

	public static Double parseDouble( String cell )
	{
		if ( cell.equalsIgnoreCase( "nan" )
				|| cell.equalsIgnoreCase( "na" )
				|| cell.equals( "" ) )
			return Double.NaN;
		else if ( cell.equalsIgnoreCase( "inf" ) )
			return Double.POSITIVE_INFINITY;
		else if ( cell.equalsIgnoreCase( "-inf" ) )
			return Double.NEGATIVE_INFINITY;
		else
			return Double.parseDouble( cell );
	}

	public static void saveTable( JTable table, File tableOutputFile )
	{
		try
		{
			saveTableWithIOException( table, tableOutputFile );
		} catch ( IOException e )
		{
			e.printStackTrace();
		}
	}

	private static void saveTableWithIOException( JTable table, File file ) throws IOException
	{
		BufferedWriter bfw = new BufferedWriter( new FileWriter( file ) );

		final int lastColumn = table.getColumnCount() - 1;

		// header
		for ( int col = 0; col < lastColumn; col++ )
			bfw.write( table.getColumnName( col ) + "\t" );
		bfw.write( table.getColumnName( lastColumn ) + "\n" );

		// content
		for ( int row = 0; row < table.getRowCount(); row++ )
		{
			for ( int col = 0; col < lastColumn; col++ )
				bfw.write( table.getValueAt( row, col ) + "\t" );
			bfw.write( table.getValueAt( row, lastColumn ) + "\n" );
		}

		bfw.close();
	}

	public static double[] getCalibration( ImagePlus imp )
	{
		double[] calibration = new double[ 3 ];

		calibration[ X ] = imp.getCalibration().pixelWidth;
		calibration[ Y ] = imp.getCalibration().pixelHeight;
		calibration[ Z ] = imp.getCalibration().pixelDepth;

		return calibration;
	}

	public static String autoDelim( String delim, List< String > strings )
	{
		if ( delim == null )
		{
			if ( strings.get( 0 ).contains( "\t" ) )
			{
				delim = "\t";
			} else if ( strings.get( 0 ).contains( "," ) )
			{
				delim = ",";
			} else if ( strings.get( 0 ).contains( ";" ) )
			{
				delim = ";";
			} else
			{
				throw new RuntimeException( "Could not identify table delimiter." );
			}

		}
		return delim;
	}

	public static void divide( double[] doubles, double factor )
	{
		for ( int i = 0; i < doubles.length; ++i )
			doubles[ i ] /= factor;
	}


	public static long countNonZeroPixelsAlongAxis(
			RandomAccessibleInterval< BitType > rai,
			int axis )
	{
		// Set position at zero
		final RandomAccess< BitType > access = rai.randomAccess();
		access.setPosition( new long[ rai.numDimensions() ] );

		long numNonZeroPixels = 0;
		for ( long coordinate = rai.min( axis ); coordinate <= rai.max( axis ); ++coordinate )
		{
			access.setPosition( coordinate, axis );
			if ( access.get().get() ) numNonZeroPixels++;
		}

		return numNonZeroPixels;
	}

	public static double getAngle( double[] v1, double[] v2 )
	{
		double le = LinAlgHelpers.length( v1 ) * LinAlgHelpers.length( v2 );
		double alpha = 0.0D;
		if (le > 0.0D) {
			double sca = LinAlgHelpers.dot( v1, v2 );
			sca /= le;
			if (sca < 0.0D) {
				sca *= -1.0D;
			}

			alpha = Math.acos(sca);
		}

		return alpha;
	}

	public static < T extends NumericType< T > & NativeType< T > >
	RandomAccessibleInterval createTransformedView( RandomAccessibleInterval< T > rai, InvertibleRealTransform transform )
	{
		final RandomAccessible transformedRA = createTransformedRaView( rai, transform, new ClampingNLinearInterpolatorFactory() );
		final FinalInterval transformedInterval = createBoundingIntervalAfterTransformation( rai, transform );
		final RandomAccessibleInterval< T > transformedIntervalView = Views.interval( transformedRA, transformedInterval );

		return transformedIntervalView;
	}

	public static < T extends NumericType< T > >
	RandomAccessible createTransformedRaView(
			RandomAccessibleInterval< T > rai,
			InvertibleRealTransform combinedTransform,
			InterpolatorFactory interpolatorFactory )
	{
		RealRandomAccessible rra = Views.interpolate( Views.extendZero( rai ), interpolatorFactory );
		rra = RealViews.transform( rra, combinedTransform );
		return Views.raster( rra );
	}

	public static long measureSizeInPixels( RandomAccessibleInterval< BitType > mask )
	{

		final Cursor< BitType > cursor = Views.iterable( mask ).cursor();
		long size = 0;

		while ( cursor.hasNext() )
		{
			if( cursor.next().get() )
			{
				size++;
			}
		}

		return size;

	}



	public static < I extends IntegerType< I > >
	long measureSizeInPixels( RandomAccessibleInterval< I > labeling,
							  int label )
	{
		final Cursor< I > labelCursor = Views.iterable( labeling ).localizingCursor();
		long size = 0;

		while ( labelCursor.hasNext() )
		{
			long value = labelCursor.next().getInteger();

			if( value == label )
			{
				size++;
			}
		}

		return size;
	}

	public static double sum( List<Double> values ){
		if (values.size() > 0) {
			double sum = 0;
			for (Double d : values) {
				sum += d;
			}
			return sum;
		}
		return 0;
	}

	public static FinalInterval createScaledInterval( Interval interval, Scale scale )
	{
		int n = interval.numDimensions();

		long[] min = new long[ n ];
		long[] max = new long[ n ];
		interval.min( min );
		interval.max( max );

		for ( int d = 0; d < n; ++d )
		{
			min[ d ] *= scale.getScale( d );
			max[ d ] *= scale.getScale( d );
		}

		return new FinalInterval( min, max );
	}

	public static double median( List<Double> values )
	{
		final ArrayList< Double > sorted = new ArrayList<>( values );

		Collections.sort( sorted );

		if ( sorted.size() == 1 ) return sorted.get( 0 );
		if ( sorted.size() == 0 ) return 0;

		int middle = sorted.size()/2;

		if (sorted.size() % 2 == 1) {
			return sorted.get(middle);
		} else {
			return (sorted.get(middle-1) + sorted.get(middle)) / 2.0;
		}
	}

	public static < R extends RealType< R > & NativeType< R > >
	RandomAccessibleInterval< R > createEmptyCopy( RandomAccessibleInterval< R > image )
	{
		RandomAccessibleInterval< R > copy =
				new ArrayImgFactory( Util.getTypeFromInterval( image ) ).create( image );
		copy = Views.translate( copy, Intervals.minAsLongArray( image ) );
		return copy;
	}

	public static < T extends RealType< T > & NativeType< T > >
	ArrayList< Double > getValuesWithinMaskAsList(
			final RandomAccessibleInterval< T > rai,
			final RandomAccessibleInterval< BitType > mask )
	{
		final Cursor< BitType > maskCursor = Views.iterable( mask ).cursor();
		final RandomAccess< T > intensityAccess = rai.randomAccess();

		intensityAccess.setPosition( maskCursor );

		final ArrayList< Double > doubles = new ArrayList<>();

		while ( maskCursor.hasNext() )
		{
			if ( maskCursor.next().get() )
			{
				intensityAccess.setPosition( maskCursor );
				doubles.add( intensityAccess.get().getRealDouble() );
			}
		}

		return doubles;
	}

	public static < T extends RealType< T > & NativeType< T > >
	ImgLabeling< Integer, IntType > asImgLabeling(
			RandomAccessibleInterval< T > masks,
			ConnectedComponents.StructuringElement structuringElement )
	{

		RandomAccessibleInterval< IntType > labelImg = ArrayImgs.ints( Intervals.dimensionsAsLongArray( masks ) );
		labelImg = getWithAdjustedOrigin( masks, labelImg );
		final ImgLabeling< Integer, IntType > imgLabeling = new ImgLabeling<>( labelImg );

		final java.util.Iterator< Integer > labelCreator = new java.util.Iterator< Integer >()
		{
			int id = 1;

			@Override
			public boolean hasNext()
			{
				return true;
			}

			@Override
			public synchronized Integer next()
			{
				return id++;
			}
		};

		final RandomAccessibleInterval< UnsignedIntType > unsignedIntTypeRandomAccessibleInterval =
				Converters.convert(
						masks,
						( i, o ) -> o.set( i.getRealDouble() > 0 ? 1 : 0 ),
						new UnsignedIntType() );

		ConnectedComponents.labelAllConnectedComponents(
				Views.extendBorder( unsignedIntTypeRandomAccessibleInterval ),
				imgLabeling,
				labelCreator,
				structuringElement );

		return imgLabeling;
	}


	public static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< T > getEnlargedRai( RandomAccessibleInterval< T > rai )
	{
		long[] min = new long[ 2 ];
		long[] max = new long[ 2 ];
		rai.max( max );
		for ( int d = 0; d < 2; ++d )
		{
			max[ d ] *= 1.2;
		}
		final FinalInterval interval = new FinalInterval( min, max );
		return Views.interval( Views.extendZero( rai ), interval );
	}

	public static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< T > getEnlargedRai( RandomAccessibleInterval< T > rai, int border )
	{
		int n = rai.numDimensions();

		long[] min = new long[ n ];
		long[] max = new long[ n ];

		rai.min( min );
		rai.max( max );

		for ( int d = 0; d < n; ++d )
		{
			min[ d ] -= border;
			max[ d ] += border;

		}

		final FinalInterval interval = new FinalInterval( min, max );
		return Views.interval( Views.extendZero( rai ), interval );
	}


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

	public static < T extends RealType< T > >
	Double getMaximumValue( RandomAccessibleInterval< T > rai )
	{
		Cursor< T > cursor = Views.iterable( rai ).cursor();

		double maxValue = Double.MIN_VALUE;

		double value;
		while ( cursor.hasNext() )
		{
			value = cursor.next().getRealDouble();
			if ( value > maxValue )
				maxValue = value;
		}

		return maxValue;
	}


	public static double[] as3dDoubleArray( double value )
	{
		double[] array = new double[ 3 ];
		Arrays.fill( array, value );
		return array;
	}

}
