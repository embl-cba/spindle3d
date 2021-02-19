package test;

import de.embl.cba.spindle3d.Spindle3DCommand;
import net.imagej.ImageJ;
import net.imglib2.type.numeric.RealType;
import org.junit.Test;
import org.renjin.gnur.api.R;

import java.io.File;

public class TestSpindleMorphometryWithCellMask
{
	public static < R extends RealType< R > > void main( String[] args )
	{
		new TestSpindleMorphometryWithCellMask().test();
	}

	@Test
	public void test()
	{
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		final Spindle3DCommand< ? > command = new Spindle3DCommand<>();
		command.opService = ij.op();
		command.scriptService = ij.script();

		// Spindle touching objects
		command.inputImageFile = new File("src/test/with-cell-mask/20210204_HeLa_MCB309_006-1.tif" );

		command.outputDirectory = new File( "src/test/resources/test/output" );
		command.spindleChannelIndexOneBased = 1;
		command.dnaChannelIndexOneBased = 2;
		command.showIntermediateImages = false;
		command.showIntermediatePlots = false;
		command.saveResults = true;
		command.run();
	}
}
