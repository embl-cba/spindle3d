import de.embl.cba.spindle3d.Spindle3DAdvancedCommand;
import de.embl.cba.tables.FileUtils;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import net.imglib2.type.numeric.RealType;

import java.io.File;
import java.util.List;

public class RunAdvancedSpindleMorphometryBatch
{
	public static < R extends RealType< R > > void main( String[] args )
	{
		DebugTools.setRootLevel("OFF");

		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		final Spindle3DAdvancedCommand< R > command = new Spindle3DAdvancedCommand<>();
		command.opService = ij.op();
		command.scriptService = ij.script();
		command.macroFile = new File( "/Users/tischer/Documents/spindle3d/scripts/roi-detection.ijm" );

		List< File > fileList = FileUtils.getFileList( new File( "/Users/tischer/Documents/spindle3d/src/test/resources/multiple-dna" ), ".*", false );

		for ( File file : fileList )
		{
			if ( ! file.getName().endsWith( ".tif" ) ) continue;

			command.inputImageFile = file;
			command.outputDirectory = new File( "/Users/tischer/Desktop/Desktop/kletter/multiple-dna" );
			command.dnaChannelIndexOneBased = 1;
			command.spindleChannelIndexOneBased = 2;
			command.showIntermediateImages = false;
			command.saveResults = true;

			command.run();
		}
	}
}
