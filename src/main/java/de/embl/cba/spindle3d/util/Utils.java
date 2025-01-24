package de.embl.cba.spindle3d.util;

import ij.ImagePlus;
import ij.measure.Calibration;
import net.imglib2.*;
import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.Scale;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static de.embl.cba.spindle3d.Spindle3DAlgorithms.getWithAdjustedOrigin;

public class Utils
{

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
