/*
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    Copyright (C) 2015 George Antony Papadakis (gpapadis@yahoo.gr)
 */

package SupervisedMetablocking;



import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;
import java.util.Set;

import DataStructures.AbstractBlock;
import DataStructures.BilateralBlock;
import DataStructures.Comparison;
import DataStructures.EntityIndex;
import DataStructures.IdDuplicates;
import DataStructures.UnilateralBlock;
import Utilities.ComparisonIterator;
import Utilities.Constants;
import Utilities.Converter;
import Utilities.ExecuteBlockComparisons;
import Utilities.ProfileComparison;
import Utilities.StatisticsUtilities;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SystemInfo;

/**
 *
 * @author gap2
 */

public abstract class AbstractSupervisedMetablocking implements Constants {

	protected final boolean dirtyER;
	protected int noOfAttributes;
	protected int noOfClassifiers;
	protected double noOfBlocks;
	protected double validComparisons;
	protected double[] comparisonsPerBlock;
	protected double[] nonRedundantCPE;
	protected double[] redundantCPE;
	//protected int[] Nblocks;

	protected Attribute classAttribute;
	protected ArrayList<Attribute> attributes;
	protected final EntityIndex entityIndex;
	protected final EntityIndex entityIndex_cpy;
	protected Instances trainingInstances;
	// List<Class1> list = new ArrayList<Class1>();
	protected List<AbstractBlock> blocks;
	protected final List<AbstractBlock> blocks_cpy;
	protected List<Double>[] overheadTimes;
	protected List<Double>[] resolutionTimes;
	protected List<Double> sampleMatches;
	protected List<Double> sampleNonMatches;
	protected List<Double> sampleNonMatchesNotUsed;
	protected List<Double>[] sampleComparisons;
	protected List<Double>[] sampleDuplicates;
	protected List<String> classLabels;
	protected final Set<IdDuplicates> duplicates;
	protected Set<Comparison> trainingSet;
	protected Set<IdDuplicates> detectedDuplicates;
	protected double lposit=0;
	protected int elements[];
	protected Hashtable balance = new Hashtable();
	protected final String names[]=(new Converter()).atributos_value;
	int Nblocks[][];
	ExecuteBlockComparisons ebcX;
	String set="";
	double th=0;
	ArrayList<Comparison>[] levels ;
	//int blockSize[]=new int[1000000];
	
	public AbstractSupervisedMetablocking (int classifiers, List<AbstractBlock> bls, List<AbstractBlock> bls_cpy, Set<IdDuplicates> duplicatePairs, ExecuteBlockComparisons ebc) {
		blocks = bls;
		blocks_cpy=bls_cpy;
		dirtyER = blocks.get(0) instanceof UnilateralBlock;
		entityIndex_cpy = new EntityIndex(blocks_cpy);
		entityIndex = new EntityIndex(blocks);
		ebcX=ebc;
		duplicates = duplicatePairs;
		noOfClassifiers = classifiers;

		
		
	}

	protected abstract void applyClassifier(Classifier classifier) throws Exception;
	protected abstract List<AbstractBlock> gatherComparisons();
	protected abstract void initializeDataStructures();
	protected abstract void processComparisons(int configurationId, int iteration, BufferedWriter writer, double th2);
	protected abstract void savePairs(int i, ExecuteBlockComparisons ebc);
	protected abstract int getCount();


	public void applyProcessing(int iteration, Classifier[] classifiers, ExecuteBlockComparisons ebc, int tamanho, BufferedWriter writer1, int r, String profilesPathA) throws Exception {
		elements=new int[10];
		overheadTimes = new ArrayList[4];
		for (int i = 0; i < overheadTimes.length; i++) {
			overheadTimes[i] = new ArrayList<Double>();
		}
		
		long startingTime = System.currentTimeMillis();
		getStatistics(tamanho);
		prepareStatistics();
		getAttributes();
		
		double overheadTime0 = System.currentTimeMillis()-startingTime;
		overheadTimes[0].add((double) overheadTime0);
		System.out.println("sampling time xxx\t:\t" + overheadTime0);
		//ebcX=ebc;
		set=profilesPathA;
		startingTime = System.currentTimeMillis();
		getTrainingSet_BLOSS(iteration,ebc,tamanho,r,profilesPathA);
		overheadTime0 = System.currentTimeMillis()-startingTime;
		overheadTimes[0].add((double) overheadTime0);
		long overheadTime1;
		System.out.println("training time rank+selection+allac \t:\t" + overheadTime0);
		//getTrainingSet(iteration);
		//System.out.println(trainingInstances.size() + "  ----- " +temp);

		for (int i = 0; i < classifiers.length; i++) {
			System.out.println("\n\nClassifier id\t:\t" + i);
			initializeDataStructures();


			startingTime = System.currentTimeMillis();
			classifiers[i].buildClassifier(trainingInstances);
			applyClassifier(classifiers[i]);
			//	System.out.println("count ---> "+ getCount());
			overheadTime1 = System.currentTimeMillis()-startingTime;
			System.out.println("classification time\t:\t" + overheadTime1);
			overheadTimes[i].add((double) overheadTime1);
			//System.out.println("----------" +getCount());
			//commented out for faster experiments
			//use when measuring resolution time
			
			System.out.println("CL"+i+" Classification time\t:\t" + (overheadTime1));
			resolutionTimes[i].add(new Double(overheadTime0+overheadTime1));

			processComparisons(i, iteration, writer1,th);
			savePairs(i,ebc);
		}
		
	}

	protected boolean areMatching(Comparison comparison) {
		if (dirtyER) {
			final IdDuplicates duplicatePair1 = new IdDuplicates(comparison.getEntityId1(), comparison.getEntityId2());
			final IdDuplicates duplicatePair2 = new IdDuplicates(comparison.getEntityId2(), comparison.getEntityId1());
			return duplicates.contains(duplicatePair1) || duplicates.contains(duplicatePair2);
		}

		final IdDuplicates duplicatePair1 = new IdDuplicates(comparison.getEntityId1(), comparison.getEntityId2());
		return duplicates.contains(duplicatePair1);
	}

	private void getAttributes() {
		attributes = new ArrayList<Attribute>();
		attributes.add(new Attribute("ECBS"));
		attributes.add(new Attribute("RACCB"));
		attributes.add(new Attribute("JaccardSim"));
		attributes.add(new Attribute("NodeDegree1"));
		attributes.add(new Attribute("NodeDegree2"));
		//attributes.add(new Attribute("teste1"));
		//	attributes.add(new Attribute("teste2"));
		attributes.add(new Attribute("sim"));
		//	attributes.add(new Attribute("teste3"));

		classLabels = new ArrayList<String>();
		classLabels.add(NON_MATCH);
		classLabels.add(MATCH);

		classAttribute = new Attribute("class", classLabels);
		attributes.add(classAttribute);
		noOfAttributes = attributes.size();
	}

	private void getStatistics(int tamanho) {
		int apagar1=0,apagar2=0;
		
		levels= (ArrayList<Comparison>[])new ArrayList[40];
		for (int i = 0; i < levels.length; i++) {
			levels[i]=new ArrayList<Comparison>();
		}
		noOfBlocks = blocks.size();
		validComparisons = 0;
		int noOfEntities = entityIndex.getNoOfEntities();
		int controle[] = new int[40];
		redundantCPE = new double[noOfEntities];
		nonRedundantCPE = new double[noOfEntities];
		int level,j ,w=0;
		Random rn = new Random();
		int apagart=0,apagarf=0;
		comparisonsPerBlock = new double[(int)(blocks.size() + 1)];
		for (AbstractBlock block : blocks) {
			comparisonsPerBlock[block.getBlockIndex()] = block.getNoOfComparisons();
			int blockSize=(int) block.getNoOfComparisons();
			List<Comparison> listComp = block.getComparisons();
			w=0;
			int numberOfPairs=0;
			for (int k = 0; k< blockSize; k++) {
				Comparison comparison = listComp.get(k);

				int entityId2 = comparison.getEntityId2()+entityIndex.getDatasetLimit();
				redundantCPE[comparison.getEntityId1()]++;
				redundantCPE[entityId2]++;

				if (!entityIndex.isRepeated(block.getBlockIndex(), comparison)) {
					validComparisons++;
					nonRedundantCPE[comparison.getEntityId1()]++;
					nonRedundantCPE[entityId2]++;
				}
				apagar1++;
				if(w!=0 && k!=w || w==listComp.size())
					continue;
				
				final List<Integer> commonBlockIndices = entityIndex.getCommonBlockIndices(block.getBlockIndex(), comparison);
				if (commonBlockIndices == null) {
					continue;
				}
				double ibf1 = Math.log(noOfBlocks/entityIndex.getNoOfEntityBlocks(comparison.getEntityId1(), 0));
				double ibf2 = Math.log(noOfBlocks/entityIndex.getNoOfEntityBlocks(comparison.getEntityId2(), 1));
				double	valor = commonBlockIndices.size()*ibf1*ibf2;	
				if(valor>1000)
					level=35;
				else
					level=(int) Math.floor(valor/30);
				
				if(controle[level]<tamanho){
					comparison.setBlockId(block.getBlockIndex());
					comparison.setSim(valor);
					levels[level].add(comparison);
					controle[level]++;
				}
				else{			
					 j = rn.nextInt(controle[level]);					
					 if(j<block.getNoOfComparisons()){						 
						 levels[level].remove(rn.nextInt(tamanho)); 
						 comparison.setBlockId(block.getBlockIndex());
						 comparison.setSim(valor);
						 comparison.setLabel(areMatching(comparison)==true?1:0);
						 levels[level].add(comparison);
						 if(tamanho<blockSize){
							 w+=k+rn.nextInt(blockSize);
						 }
						 if(numberOfPairs++>3)
							 w=blockSize;
					 }
					 controle[level]++; 
				}
				
		
			}
		}
		System.out.println("apafgarrrrrrrrrrr "+ apagar1 +"  "+ apagar2);
	/*	for (int i = 0; i < levels.length; i++) {
			for(Comparison c:levels[i]){
				System.out.print("block size " + i +"  "+ c.getSim() +"  " + c.getBlockId() +"  j  "+w++ +  " ");
				if(c.getLabel()==1)
					apagart++;
				else
					apagarf++;
			}
			System.out.println();
		}
		System.out.println("positios " + apagart +"  "+ apagarf);*/
	}
	


	protected Instance getFeatures(int match, List<Integer> commonBlockIndices,List<Integer> commonBlockIndices_cpy,Comparison comparison, double flag) {
		double[] instanceValues =null;
		instanceValues = new double[noOfAttributes];
		
		int entityId2 = comparison.getEntityId2() + entityIndex.getDatasetLimit();
		
		 if (blocks.get(0) instanceof BilateralBlock) {
			 instanceValues[5] =ebcX.jaccardSimilarity_l_real(comparison.getEntityId1(),comparison.getEntityId2(), commonBlockIndices_cpy.size()); // getSimilarityAttribute_l(comparison.getEntityId1(), comparison.getEntityId2());;//ebcX.jaccardSimilarity_l(comparison.getEntityId1(),comparison.getEntityId2());//ebcX.getSimilarityAttribute(comparison.getEntityId1(), comparison.getEntityId2());;//ebcX.jaccardSimilarity_l(comparison.getEntityId1(),comparison.getEntityId2());
	        } else if (blocks.get(0) instanceof UnilateralBlock) {
	        	instanceValues[5] = ebcX.jaccardSimilarity_l(comparison.getEntityId1(),comparison.getEntityId2(), commonBlockIndices_cpy.size()); // getSimilarityAttribute_l(comparison.getEntityId1(), comparison.getEntityId2());;//ebcX.jaccardSimilarity_l(comparison.getEntityId1(),comparison.getEntityId2());//ebcX.getSimilarityAttribute(comparison.getEntityId1(), comparison.getEntityId2());;//ebcX.jaccardSimilarity_l(comparison.getEntityId1(),comparison.getEntityId2());
	        }
//		 double temp= ebcX.getSimilarityAttribute(comparison.getEntityId1(),comparison.getEntityId2());
//		 if(Math.abs(temp-instanceValues[5])>0.1) {
//			 System.out.println(" erroo  " + temp+ " "+ instanceValues[5]);
//			 instanceValues[5] =ebcX.jaccardSimilarity_l_real(comparison.getEntityId1(),comparison.getEntityId2(), commonBlockIndices_cpy.size()); // getSimilarityAttribute_l(comparison.getEntityId1(), comparison.getEntityId2());;//ebcX.jaccardSimilarity_l(comparison.getEntityId1(),comparison.getEntityId2());//ebcX.getSimilarityAttribute(comparison.getEntityId1(), comparison.getEntityId2());;//ebcX.jaccardSimilarity_l(comparison.getEntityId1(),comparison.getEntityId2());
//		 }
//		
		if(instanceValues[5]<0.1 && flag==1.0)
		{

			return null;
		}
		double ibf1 = Math.log(noOfBlocks/entityIndex.getNoOfEntityBlocks(comparison.getEntityId1(), 0));
		double ibf2 = Math.log(noOfBlocks/entityIndex.getNoOfEntityBlocks(comparison.getEntityId2(), 1));
		try{
			instanceValues[0] = commonBlockIndices.size()*ibf1*ibf2;	
		}catch (Exception e ){
			System.out.println(e.getMessage());
		}
		
		double raccb = 0;
		for (Integer index : commonBlockIndices) {
			raccb += 1.0 / comparisonsPerBlock[index];
		}
		if (raccb < 1.0E-6) {
			raccb = 0.0000006;
		}

		instanceValues[1] = raccb;
		
		instanceValues[2] = commonBlockIndices.size() / (redundantCPE[comparison.getEntityId1()] + redundantCPE[entityId2] - commonBlockIndices.size());
		instanceValues[3] = nonRedundantCPE[comparison.getEntityId1()];
		instanceValues[4] = nonRedundantCPE[entityId2];
		
		instanceValues[6] = match;
		
		Instance newInstance = new DenseInstance(1.0, instanceValues);
		newInstance.setDataset(trainingInstances);
		return newInstance;
	}

	
	protected void getTrainingSet_BLOSS(int iteration, ExecuteBlockComparisons ebc, int tamanho, int r, String profilesPathA) throws FileNotFoundException {
		
		sampleMatches.clear();
		sampleNonMatches.clear();
		sampleNonMatchesNotUsed.clear();
		
		Random random= new Random(iteration);
		PrintStream psarff = null;
		int matchingInstances = (int) (SAMPLE_SIZE*duplicates.size());
		
		double nonMatchRatio = matchingInstances / (validComparisons - duplicates.size());
		System.out.println("nonMatchRatio --> " + nonMatchRatio  + " duplicates.size() "+ duplicates.size() + " validComparisons " + validComparisons);
		trainingSet = new HashSet<Comparison>(4*matchingInstances);
		trainingInstances = new Instances("trainingSet", attributes, 2*matchingInstances);
		trainingInstances.setClassIndex(noOfAttributes - 1);
		
		
		long startingTime = System.currentTimeMillis();
		//List<Comparison> listComparison = conta_niveis_hash(blocks,ebc,tamanho);
		//	System.out.println("count ---> "+ getCount());
		long overheadTime = System.currentTimeMillis()-startingTime;
		System.out.println("rank time\t:\t" + overheadTime);

		startingTime = System.currentTimeMillis();
		
		try {
			psarff = new PrintStream(new FileOutputStream(new File("/tmp/levels_arff"+profilesPathA+".arff"),false));
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
		psarff.println("@relation whatever");
		for (int i = 0; i < trainingInstances.numAttributes()-1 ; i++) {
			psarff.println("@attribute "+i+" numeric");			
		}		
		psarff.println("@attribute classe {0,1}");
		psarff.println("@data");
		//Vector<Comparison> randomInstances= new Vector<Comparison>(4*matchingInstances);;
		int countp=0;
		int countn=0;
		for (int i = 0; i < levels.length; i++) {
			//for (int j = 0; j < levels[i].length; j++) {
			for(Comparison c:levels[i]){
				getLevels(c,psarff);
			}
		}

		
//		overheadTime = System.currentTimeMillis()-startingTime;
		
		startingTime = System.currentTimeMillis();
		
		psarff.close();

		try {
			File f = new File("/tmp/lock");
			while(f.exists() ) { 
				System.out.println("sleeping................");
				Thread.sleep(1000);
			}
			f.createNewFile();

			callGeraBins();
			callAllac(8,r);  

			loadFileTrainingSet();
			f.delete();
		}  catch (Exception e) {
			e.printStackTrace();
		}
		overheadTime = System.currentTimeMillis()-startingTime;
		System.out.println("training  time\t:\t" + overheadTime);
	}
		

	private void loadFileTrainingSet() throws Exception {
		// TODO Auto-generated method stub
		BufferedReader alac_result = new BufferedReader(new FileReader("/tmp/final_treina.arff"));
		//BufferedReader alac_result = new BufferedReader(new FileReader("/tmp/levels_arffdataset.arff"));
		Instances data = new Instances(alac_result);
		data.setClassIndex(data.numAttributes() -1);
		int countP=0,countN=0, countDesc=0;
		double positivos=0.0, negativos=0.0;
		int histograma[][]=new int[11][2];
		lposit=data.get(0).value(0);
		for (Instance instance : data) {
			if((instance.value(data.numAttributes() -1))==1){
				positivos+=instance.value(data.numAttributes() -2);
				//histograma[(int) Math.floor(instance.value(data.numAttributes() -2)*10)][0]++;
				//System.out.println(instance.value(data.numAttributes()-2)+ " P");
				if(lposit>instance.value(data.numAttributes() -2)){
					//System.out.println(instance.value(data.numAttributes() -2));
					lposit=instance.value(data.numAttributes() -2);
					
				}
			}
			else{
				negativos+=instance.value(data.numAttributes() -2);
				//histograma[(int) Math.floor(instance.value(data.numAttributes() -2)*10)][1]++;
				System.out.println(instance.value(data.numAttributes()-2)  +"   "+ (int) Math.floor(instance.value(data.numAttributes() -2)*10));
				//System.out.println(instance.value(data.numAttributes()-2)+ " N");
			}
			
			if((instance.value(data.numAttributes() -1))==1.0)  
				countP++;
			else
				countN++;
			
		}

		System.out.println("menor positivo " + lposit);
	//	th=((int)(th*100))/100.0;
		th=((((negativos/countN)*10)))/10.0;
		System.out.println(" media " + th +  "  "+ (negativos/countN));
		int temp =(int)(th*10);
		double temp_t=th*10;
		if(temp==0)
		th=0.1;
		else
			if(Math.abs(temp_t -temp)>=0.5)
				th=Math.ceil(th*10)/10;
			else
				th=Math.floor(th*10)/10;
		
		System.out.println("threshold ---> " + th);
//		if(set.contains("dblp"))
	//	if(th==0)
	//	th=0.1;
		//if(set.contains("dblp"))
		//	th=0.2;
		for (Instance instance : data) {
			if((instance.value(data.numAttributes() -1)==0.0) && (instance.value(instance.numAttributes()-2))>= th)
			{				
				countDesc++;
				//System.out.println("descartando.........." + instance.value(instance.numAttributes()-2));
				continue;
			}		
			if((instance.value(data.numAttributes() -1)==0.0))
				System.out.println(instance.value(instance.numAttributes()-2)  + "  "+ ebcX.temp_limiar);
			trainingInstances.add(instance);
			
		}
		
		System.out.println("valores  --> Positio -> " +countP  +"  negativos -> "+(countN-countDesc) + "   countDesc -->"+countDesc);
		sampleMatches.add((double) countP);///positivos
		sampleNonMatches.add((double) (countN)); //negativos
		sampleNonMatchesNotUsed.add((double) (countDesc)); //negativos
	}

	
//	private List<Comparison> conta_niveis_hash(List<AbstractBlock> blocks, ExecuteBlockComparisons ebc, int tamanho) {
//
//		Nblocks=  new int[100][3];
//		List<Comparison> levels=new ArrayList<Comparison>(); 
////		for (int i = 0; i < 100; i++) {
////			blockSize[i][0]=0;
////			blockSize[i][1]=0;
////			blockSize[i][2]=0;
////			//	primeiroBlock[i]=0;
////		}
//		double sim=0.0;
//		
//		//Collections.shuffle(blocks);
//		int level=0;
//		Random rn = new Random();
//		List<Comparison> listComparison;
//		List<Integer> commonBlockIndices=null;
//		for ( AbstractBlock b:blocks) {
//			listComparison = b.getComparisons();
//			int i =1;
//
//			
//			while(i < listComparison.size() ) 
//			{
//				
//				Comparison c=listComparison.get(i-1);
//				commonBlockIndices = entityIndex.getCommonBlockIndices(b.getBlockIndex(), c);
//				if ( commonBlockIndices == null) {
//					i++;
//					continue;
//				}
//			double ibf1 = Math.log(noOfBlocks/entityIndex.getNoOfEntityBlocks(c.getEntityId1(), 0));
//			double ibf2 = Math.log(noOfBlocks/entityIndex.getNoOfEntityBlocks(c.getEntityId2(), 1));
//			try{
//				sim = commonBlockIndices.size()*ibf1*ibf2;	
//			}catch (Exception e ){
//				System.out.println(e.getMessage());
//			}
//			level=0;
//			if(sim>1000){
//				level=((int)Math.floor(35));
//			}else
//				level=((int)Math.floor(sim/30));
//				
////			    final List<Integer> commonBlockIndices_cpy = entityIndex_cpy.getCommonBlockIndices_cpy(b.getBlockIndex(), c);
////				//final List<Integer> commonBlockIndices_cpy = entityIndex.getCommonBlockIndices(b.getBlockIndex(), c);
////				sim =ebc.jaccardSimilarity_l(c.getEntityId1(), c.getEntityId2(), commonBlockIndices_cpy.size());
////				if(sim>1.0){
////					System.out.println("sim " + sim);
////					sim=1.0;
////					
////				}
////				if(Nblocks[(int)(sim*10)][0] > tamanho)
////					continue;
////			while (numbers.contains(sem)) {
////				sem=rn.nextInt(listComparison.size());
////		    }
////			numbers.add(sem);
//			
//			
//			Nblocks[level][0]++;
//				
//					
//				
//				c.setBlockId(b.getBlockIndex());
//				c.setSim(level);
//				c.setLabel(areMatching(c)==true?1:0);
//				if(c.getLabel()==1)
//					Nblocks[level][1]++;
//				else
//					Nblocks[level][2]++;
//				//getFeatures(0, commonBlockIndices_cpy, commonBlockIndices_cpy, c, 0);
//				levels.add(c);
//				
//				
//				if(listComparison.size()>tamanho)
//					i+=listComparison.size()/(listComparison.size()/(tamanho));
//				else		
//					i+=listComparison.size()*2+1;
//				
//				if(i>listComparison.size())
//					break;
//			}	
//			
//		}
//		for (int i = 0; i < Nblocks.length; i++) {
//			if(Nblocks[i][0]>0)
//				System.out.println("tamanho bloco " +Nblocks[i][0] + " " + Nblocks[i][1] +" "+ Nblocks[i][2]);
//}
//		
//
//		return levels;
//
//
//
//	}

//	private int[][] conta_niveis_hashB(List<AbstractBlock> blocks, ExecuteBlockComparisons ebc) {
//
//		int[][] blockSize=  new int[100][3];
//		for (int i = 0; i < 100; i++) {
//			blockSize[i][0]=0;
//			blockSize[i][1]=0;
//			blockSize[i][2]=0;
//			//	primeiroBlock[i]=0;
//		}
//		double sim=0.0;
//		
//		
//		for ( AbstractBlock b:blocks) {
//
//
//			ComparisonIterator iterator = b.getComparisonIterator();
//			Comparison c;
//			while(iterator.hasNext()){			
//				c= iterator.next(); 
//
//				final List<Integer> commonBlockIndices = entityIndex.getCommonBlockIndices(b.getBlockIndex(), c);
//				if (commonBlockIndices == null) {
//					continue;
//				}
//				
//				double ibf1 = Math.log(noOfBlocks/entityIndex.getNoOfEntityBlocks(c.getEntityId1(), 0));
//				double ibf2 = Math.log(noOfBlocks/entityIndex.getNoOfEntityBlocks(c.getEntityId2(), 1));
//				try{
//					sim = commonBlockIndices.size()*ibf1*ibf2;	
//				}catch (Exception e ){
//					System.out.println(e.getMessage());
//				}
//				int level=0;
//				if(sim>1000){
//					level=((int)Math.floor(35));
//				}else
//					level=((int)Math.floor(sim/30));
//				
//				blockSize[level][0]++;
//				if(areMatching(c))
//					blockSize[level][2]++;
//				else
//					blockSize[level][1]++;
////				else
////					blockSize[((int)Math.floor(sim/30))][0]++;
//			}
//
//		}
//		for (int i = 0; i < 100; i++) {
//			if(blockSize[i][0] !=0)
//				//	perc[i]=(((double)tamanho)/(blockSize[i]));
//				System.out.println(i + " tamanho do bloco "+  "  " + blockSize[i][0] + " " +  blockSize[i][1]  +"  "+ blockSize[i][2]);
//			//totalPares += blockHash.blockSize[i];
//		}
//		return blockSize;
//
//
//
//	}



	

	private void callGeraBins() throws IOException {
		String line;
		String cmd;
		String userHome = System.getProperty("user.home");
		String file ="/tmp/levels_arff"+set+ " /tmp/teste";
		int att=6;
		Process proc = null;		
		BufferedReader read, buf;
		//	new CriaMatrixWeka(common).criaArffActiveLearning("/tmp/levels.txt",2);
		cmd = "cd  "+ userHome+ "/Downloads/SSARP/Dedup/test5/; bash ./gera_beans.sh  " +file + "   "+ att + " "+ att;
		proc = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", cmd});

		read = new BufferedReader(new InputStreamReader(proc.getInputStream()));
		try {
			proc.waitFor();
		} catch (InterruptedException e) {
			System.out.println(e.getMessage());
		}
		buf = new BufferedReader(new InputStreamReader(proc.getInputStream()));
		while ((line=buf.readLine())!=null) {
			System.out.println(line);
		}
		while (read.ready()) {
			System.out.println(read.readLine());
		}
	}

	int elemento=0;



	private void callAllac(int i, int r) throws IOException {
		////Common common=new Common();
		//common.setSortFile("/");
		//common.setElementos("3,4,5,6");
		String line;
		String cmd;
		String userHome = System.getProperty("user.home");
		//String file ="/tmp/levels_arff_level"+i+"" + " /tmp/teste";
		String file ="/tmp/levels_arff" +set + " /tmp/teste";
		System.out.println(" ----" + file + " ----");
		int att=6;
		Process proc = null;		
		BufferedReader read, buf;
		cmd = "cd  "+ userHome+ "/Downloads/SSARP/Dedup/test5/; bash ./SSARP2.sh  " +file + " "+ i +" " +att + "  "+ r;
		proc = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", cmd});
		System.out.println("CMD 1= " +cmd);

		read = new BufferedReader(new InputStreamReader(proc.getInputStream()));
		try {
			proc.waitFor();
		} catch (InterruptedException e) {
			System.out.println(e.getMessage());
		}
		buf = new BufferedReader(new InputStreamReader(proc.getInputStream()));
		while ((line=buf.readLine())!=null) {
			System.out.println(line);
		}
		while (read.ready()) {
			System.out.println(read.readLine());
		}
		System.out.println("finaliza processo");

	}

	//	private int original(Random random, Comparison comparison, int trueMetadata, List<Integer> commonBlockIndices, int trueMetadata1, double nonMatchRatio){
	//		temp++;
	//		int match = NON_DUPLICATE; // false
	//		if (areMatching(comparison)) {
	//			if (random.nextDouble() < SAMPLE_SIZE) {
	//				trueMetadata1++;
	//				match = DUPLICATE; // true
	//			} else {
	//				return trueMetadata1;
	//			}
	//		} else if (nonMatchRatio <= random.nextDouble()) {
	//			return trueMetadata1;
	//		}
	//
	//		trainingSet.add(comparison);
	//		Instance newInstance = getFeatures(match, commonBlockIndices, comparison,0.0);
	//		trainingInstances.add(newInstance);
	//		return trueMetadata1;
	//	}


	private int getLevels(Comparison comparison,  PrintStream psarff) {
//		String concatStringA;
//		String concatStringB;
//		Double sim=comparison.sim;


//		Set<DataStructures.Attribute> setAtributtes = ebc.exportEntityA(comparison.getEntityId1());
//		String sA[]=Converter.createVector(setAtributtes,comparison.getEntityId1());
//		concatStringA=sA[0]+"::";////title,
//
//		if(sA.length==0)
//			return 1;
//		for (int j = 0; j < sA.length; j++) {
//			try{
//				//System.err.print(sA[j]+ "  ");
//				if(!sA[j].isEmpty())
//					sA[j]=sA[j].replace(",", " ").replace(":", " ").replace("\n","");
//				concatStringA=concatStringA.concat(sA[j]+":");				
//
//			}catch(Exception e){
//				concatStringA=concatStringA.concat(":  :");	
//			}
//		}
//		setAtributtes = ebc.exportEntityB(comparison.getEntityId2());
//		String sB[]=Converter.createVector(setAtributtes,comparison.getEntityId2());
//		//    System.out.print( "  ---- ");
//		concatStringB=sB[0]+"::";
//		for (int j = 0; j < sB.length; j++) {
//			try{
//				//System.err.print(sB[j]+ "  ");
//				if(!sB[j].isEmpty())
//					sB[j]=sB[j].replace(",", " ").replace(":", " ").replace("\n","");
//				concatStringB=concatStringB.concat(sB[j]+":");
//			}catch (Exception e ){
//				concatStringB=concatStringB.concat(": :");
//			}
//		}
//		String label="false";
//		IdDuplicates duplicatePair1 = new IdDuplicates(comparison.getEntityId1(), comparison.getEntityId2());
//		if (duplicates.contains(duplicatePair1)) {
//			label="true";
//			//System.out.println("duplicate pair " + concatStringA + "   "+ concatStringB);
//		}
		//////////////
		//System.out.println(comparison.sim);


		//
		//double similarity = ProfileComparison.getJaccardSimilarity(ebc.exportEntityA(comparison.getEntityId1()), ebc.exportEntityB(comparison.getEntityId2()));
		final List<Integer> commonBlockIndices = entityIndex.getCommonBlockIndices(comparison.getBlockId(), comparison);
		
		final List<Integer> commonBlockIndices_cpy = entityIndex_cpy.getCommonBlockIndices_cpy(comparison.getBlockId(), comparison);
		
		Instance newInstanceTemp = getFeatures(0, commonBlockIndices,commonBlockIndices_cpy, comparison,0.0);

//		if(controle>4)
//			return 0;

		DecimalFormat decimalFormatter = new DecimalFormat("############.#####");
		String temp;

		for (int j = 0; j < newInstanceTemp.numAttributes()-1; j++) {

			temp = decimalFormatter.format(newInstanceTemp.value(j));
			temp=temp.replace(",", ".");
			//System.out.println(temp);
			psarff.print(temp + ", ");
			//psarff_level[controle].print(temp + ", ");
		}
		//psarff.print(d+", ");
		//psarff_level[controle].print(d +", ");
		psarff.println(areMatching(comparison)?1:0);
		//psarff_level[controle].println(label.contains("true")?1:0);
		///////////    	
		//FileUtilities.save_data_db( String.valueOf(i), sB[0],concatStringA,concatStringB, sim,label,null,pstxt,pstxt_level, psarff_level,k  );
		//pstxt.println(concatStringA +","+sim+ ", " +concatStringB+ ","+label+ " ,"+String.valueOf(i));
		//				pstxt_level[controle].println(concatStringA +","+sim+ ", " +concatStringB+ ","+label+ " ,"+String.valueOf(i));
		//				pstxt_level[controle].flush();
		//pstxt.flush();
		//	if(ele)
		//	elements[controle]++;
		//System.out.println("controle " + controle + " --> element "+elements[controle]);
		return -1;
	}


	//	   public static double getJaccardSimilarity(int[] tokens1, int[] tokens2) {
	//	        double commonTokens = 0.0;
	//	        int noOfTokens1 = tokens1.length;
	//	        int noOfTokens2 = tokens2.length;
	//	        for (int i = 0; i < noOfTokens1; i++) {
	//	            for (int j = 0; j < noOfTokens2; j++) {
	//	                if (tokens2[j] < tokens1[i]) {
	//	                    continue;
	//	                }
	//
	//	                if (tokens1[i] < tokens2[j]) {
	//	                    break;
	//	                }
	//
	//	                if (tokens1[i] == tokens2[j]) {
	//	                    commonTokens++;
	//	                }
	//	            }
	//	        }
	//	        return commonTokens / (noOfTokens1 + noOfTokens2 - commonTokens);
	//	    }


	private void prepareStatistics() {
		sampleMatches = new ArrayList<Double>();
		sampleNonMatches = new ArrayList<Double>();
		sampleNonMatchesNotUsed= new ArrayList<Double>();
		overheadTimes = new ArrayList[noOfClassifiers];
		resolutionTimes = new ArrayList[noOfClassifiers];
		sampleComparisons = new ArrayList[noOfClassifiers];
		sampleDuplicates = new ArrayList[noOfClassifiers];
		for (int i = 0; i < noOfClassifiers; i++) {
			overheadTimes[i] = new ArrayList<Double>();
			resolutionTimes[i] = new ArrayList<Double>();
			sampleComparisons[i] = new ArrayList<Double>();
			sampleDuplicates[i] = new ArrayList<Double>();
		}
	}


//	public void printStatistics() throws IOException {
//		System.out.println("\n\n\n\n\n+++++++++++++++++++++++Printing overall statistics+++++++++++++++++++++++");
//
//		double avSMatches = StatisticsUtilities.getMeanValue(sampleMatches);
//		double avSNonMatches = StatisticsUtilities.getMeanValue(sampleNonMatches);
//		System.out.println("Sample matches\t:\t" + avSMatches + "+-" + StatisticsUtilities.getStandardDeviation(avSMatches, sampleMatches));
//		System.out.println("Sample non-matches\t:\t" + avSNonMatches + "+-" + StatisticsUtilities.getStandardDeviation(avSNonMatches, sampleNonMatches));
//
//		for (int i = 0; i < overheadTimes.length; i++) {
//			System.out.println("\n\n\n\n\nClassifier id\t:\t" + (i));
//
//			double avOTime = StatisticsUtilities.getMeanValue(overheadTimes[i]);
//			double avRTime = StatisticsUtilities.getMeanValue(resolutionTimes[i]);
//			double avSEComparisons = StatisticsUtilities.getMeanValue(sampleComparisons[i]);
//			double avSDuplicates = StatisticsUtilities.getMeanValue(sampleDuplicates[i]);
//
//
//
//			final List<Double> pcs = new ArrayList<Double>();
//			for (int j = 0; j < sampleMatches.size(); j++) {
//				//pcs.add(sampleDuplicates[i].get(j)/(duplicates.size() - sampleMatches.get(j))*100.0);
//				System.out.println(sampleDuplicates[i].get(j) + "   "+  (duplicates.size()  +"  "+ sampleMatches.get(j)));
//				pcs.add((sampleDuplicates[i].get(j))/(duplicates.size())*100.0);
//			}
//			double avSPC = StatisticsUtilities.getMeanValue(pcs);
//
//			System.out.println("Overhead time\t:\t" + avOTime + "+-" + StatisticsUtilities.getStandardDeviation(avOTime, overheadTimes[i]));
//			System.out.println("Resolution time\t:\t" + avRTime + "+-" + StatisticsUtilities.getStandardDeviation(avRTime, resolutionTimes[i]));
//			System.out.println("Sample duplicates\t:\t" + avSDuplicates + "+-" + StatisticsUtilities.getStandardDeviation(avSDuplicates, sampleDuplicates[i]));
//			System.out.println("Sample PC\t:\t" + avSPC  + "+-  " + + StatisticsUtilities.getStandardDeviation(avSPC  , pcs));
//			System.out.println("Sample comparisons\t:\t " + avSEComparisons + "+- " + StatisticsUtilities.getStandardDeviation(avSEComparisons, sampleComparisons[i]));
//
//			//			try {
//			//				writer.write(" " +avOTime+ " "+ StatisticsUtilities.getStandardDeviation(avOTime, overheadTimes[i]));
//			//				writer.write(" " +avRTime + " " + StatisticsUtilities.getStandardDeviation(avRTime, resolutionTimes[i]));
//			//				writer.write(" " +avSDuplicates + " " + StatisticsUtilities.getStandardDeviation(avSDuplicates, sampleDuplicates[i]));
//			//				writer.write(" " +avSPC  + "  " + + StatisticsUtilities.getStandardDeviation(avSPC  , pcs));
//			//				writer.write(" " +avSEComparisons + " " + StatisticsUtilities.getStandardDeviation(avSEComparisons, sampleComparisons[i]));
//			//			} catch (IOException e) {
//			//				// TODO Auto-generated catch block
//			//				e.printStackTrace();
//			//			}
//
//
//		}
//	}
}