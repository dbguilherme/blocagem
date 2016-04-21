package Experiments;

import DataStructures.AbstractBlock;
import BlockProcessing.BlockRefinement.SizeBasedBlockPurging;
import BlockProcessing.ComparisonRefinement.AbstractDuplicatePropagation;
import BlockProcessing.ComparisonRefinement.BilateralDuplicatePropagation;
import MetaBlocking.WeightedEdgePruning;
import MetaBlocking.CardinalityEdgePruning;
import Utilities.BlockStatistics;
import MetaBlocking.CardinalityNodePruning;
import MetaBlocking.WeightedNodePruning;
import MetaBlocking.WeightingScheme;
import Utilities.ExportBlocks;
import java.io.IOException;
import java.util.List;

/**
 * vi
 *
 * @author gap2
 */
public class MetaBlockingExperiments {

    /**
     * Experiments of Table 2 in the paper 
     * "Meta-Blocking: Taking Entity Resolution to the Next Level"
     * in TKDE 2014
     * 
     */
    
    public static List<AbstractBlock> getBlocks(String[] indexDirs) throws IOException {
        ExportBlocks exportBlocks = new ExportBlocks(indexDirs);
        List<AbstractBlock> blocks = exportBlocks.getBlocks();
        System.out.println("Blocks\t:\t" + blocks.size());
        
        SizeBasedBlockPurging blockPurging = new SizeBasedBlockPurging();
        blockPurging.applyProcessing(blocks);
        System.out.println("Blocks remaining after block purging\t:\t" + blocks.size());
        
//        BlockStatistics blStats1 = new BlockStatistics(blocks, new BilateralDuplicatePropagation("C:\\Data\\Movies\\moviesIdGroundTruth"));
//        blStats1.applyProcessing();
        
        return blocks;
    }
    
    public static void main(String[] args) throws IOException {
        String mainDirectory = "C:\\Data\\Movies\\";
        String[] indexDir = { mainDirectory+"Indices\\tokenBlocking" };
        String duplicatesPath = mainDirectory + "moviesIdGroundTruth";
            
        System.out.println("\n\n\n\n\n");
        System.out.println("=================================================");
        System.out.println("++++++++++++++++Weight Edge Pruning++++++++++++++");
        System.out.println("=================================================");
        for (WeightingScheme scheme : WeightingScheme.values()) {
            System.out.println("\n\n\n\n\nWeighting scheme\t:\t" + scheme);
            List<AbstractBlock> blocks = getBlocks(indexDir);
            AbstractDuplicatePropagation adp = new BilateralDuplicatePropagation(duplicatesPath);
            
            WeightedEdgePruning ep = new WeightedEdgePruning(scheme);
            ep.applyProcessing(blocks);
            
            BlockStatistics blStats = new BlockStatistics(blocks, adp);
            blStats.applyProcessing();
        }

        System.out.println("\n\n\n\n\n");
        System.out.println("=================================================");
        System.out.println("++++++++++++++++++++Top-K Edges++++++++++++++++++");
        System.out.println("=================================================");
        for (WeightingScheme scheme : WeightingScheme.values()) {
            System.out.println("\n\n\n\n\nWeighting scheme\t:\t" + scheme);
            List<AbstractBlock> blocks = getBlocks(indexDir);
            AbstractDuplicatePropagation adp = new BilateralDuplicatePropagation(duplicatesPath);
            
            CardinalityEdgePruning tked = new CardinalityEdgePruning(scheme);
            tked.applyProcessing(blocks);
            
            BlockStatistics blStats = new BlockStatistics(blocks, adp);
            blStats.applyProcessing();
        }

        System.out.println("\n\n\n\n\n");
        System.out.println("=================================================");
        System.out.println("++++++++++++++++Weight Node Pruning++++++++++++++");
        System.out.println("=================================================");
        for (WeightingScheme scheme : WeightingScheme.values()) {
            System.out.println("\n\n\n\n\nWeighting scheme\t:\t" + scheme);
            List<AbstractBlock> blocks = getBlocks(indexDir);
            AbstractDuplicatePropagation adp = new BilateralDuplicatePropagation(duplicatesPath);
            
            WeightedNodePruning np = new WeightedNodePruning(scheme);
            np.applyProcessing(blocks);
            
            BlockStatistics blStats = new BlockStatistics(blocks, adp);
            blStats.applyProcessing();
        }

        System.out.println("\n\n\n\n\n");
        System.out.println("=================================================");
        System.out.println("++++++++++++++++k-Nearest Entities+++++++++++++++");
        System.out.println("=================================================");
        for (WeightingScheme scheme : WeightingScheme.values()) {
            System.out.println("\n\n\n\n\nWeighting scheme\t:\t" + scheme);
            List<AbstractBlock> blocks = getBlocks(indexDir);
            AbstractDuplicatePropagation adp = new BilateralDuplicatePropagation(duplicatesPath);
            
            CardinalityNodePruning knen = new CardinalityNodePruning(scheme);
            knen.applyProcessing(blocks);
            
            BlockStatistics blStats = new BlockStatistics(blocks, adp);
            blStats.applyProcessing();
        }
    }
}