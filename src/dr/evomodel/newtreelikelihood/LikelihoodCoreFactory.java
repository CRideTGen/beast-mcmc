/**
 *
 */
package dr.evomodel.newtreelikelihood;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Marc Suchard
 *
 */
public class LikelihoodCoreFactory {

	public static LikelihoodCore loadLikelihoodCore(int[] configuration, AbstractTreeLikelihood treeLikelihood) {

		if (coreRegistry == null) {  // Lazy loading
			coreRegistry = new ArrayList<LikelihoodCoreLoader>();  // List libraries in order of load-priority
			coreRegistry.add(new GPULikelihoodCore.LikelihoodCoreLoader());
            coreRegistry.add(new NativeLikelihoodCore.LikelihoodCoreLoader());
		}

		for(LikelihoodCoreLoader loader: coreRegistry) {
//            System.out.print("Attempting to load core: " + loader.getLibraryName());
            LikelihoodCore core = loader.createLikelihoodCore(configuration, treeLikelihood);
			if (core != null) {
//                System.out.println(" - SUCCESS");

				return core;
            }
//            System.out.println(" - FAILED");
		}

		// No libraries/processes available

		int stateCount = configuration[0];
		return new OTFPCGeneralLikelihoodCore(stateCount);
	}

	private static List<LikelihoodCoreLoader> coreRegistry;

	protected interface LikelihoodCoreLoader {
		public String getLibraryName();

		/**
		 * Actual factory
		 * @param configuration
		 * @param treeLikelihood  Should remove this after migration bug is solved for GPU
		 * @return
		 */
		public LikelihoodCore createLikelihoodCore(int[] configuration, AbstractTreeLikelihood treeLikelihood);
	}

}
