package de.embl.cba.spindle3d;

import de.embl.cba.morphometry.Algorithms;
import de.embl.cba.transforms.utils.Transforms;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

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

	public static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< BitType > createMask(
			RandomAccessibleInterval< T > rai,
			double threshold,
			OpService opService )
	{
		RandomAccessibleInterval< BitType > mask
				= Converters.convert( rai, ( i, o ) ->
				o.set( i.getRealDouble() > threshold ? true : false ), new BitType() );

		// "Bug" in Ops requires a Views.zeroMin().
		mask = opService.morphology().fillHoles( Views.zeroMin( mask ) );

		mask = Transforms.getWithAdjustedOrigin( rai, mask );

		return mask;
	}
}
