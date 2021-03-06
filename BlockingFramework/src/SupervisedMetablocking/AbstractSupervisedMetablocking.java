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
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;

import com.sun.org.apache.xalan.internal.xsltc.compiler.Pattern;

import DataStructures.AbstractBlock;
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
	protected Attribute classAttribute;
	protected ArrayList<Attribute> attributes;
	protected final EntityIndex entityIndex;
	protected Instances trainingInstances;
	protected List<AbstractBlock> blocks;
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
	protected int totalPares=0;
	protected int elements[];
	int Nblocks[][];
	ExecuteBlockComparisons ebc;
	String set="";
	double th=0;
	int global_threshold=30;
	double vector[][]=new double[50][2]; 

	public AbstractSupervisedMetablocking (int classifiers, List<AbstractBlock> bls, Set<IdDuplicates> duplicatePairs, ExecuteBlockComparisons ebc) {
		//ebcX=ebc;
		blocks = bls;
		dirtyER = blocks.get(0) instanceof UnilateralBlock;
		entityIndex = new EntityIndex(blocks);
		duplicates = duplicatePairs;
		noOfClassifiers = classifiers;
		this.ebc=ebc;
		getStatistics();
		prepareStatistics();
		getAttributes();
		
		
//		try {
//			Nblocks=conta_niveis_TUBE(blocks,ebc);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
	}

	protected abstract void applyClassifier(Classifier classifier) throws Exception;
	protected abstract List<AbstractBlock> gatherComparisons();
	protected abstract void initializeDataStructures();
	protected abstract void processComparisons(int configurationId, int iteration, BufferedWriter writer, BufferedWriter writer2, BufferedWriter writer3,BufferedWriter writer4, double th2);
	protected abstract void savePairs(int i, ExecuteBlockComparisons ebc);
	protected abstract int getCount();


	public void applyProcessing(int iteration, Classifier[] classifiers, int tamanho, BufferedWriter writer1, BufferedWriter writer2, BufferedWriter writer3, BufferedWriter writer4, int r, String profilesPathA) throws Exception {
		elements=new int[10];
		global_threshold=(int) ebc.temp_limiar;
		set=profilesPathA;
		Nblocks=conta_niveis_hash(blocks,ebc);
		getTrainingSet_original(iteration,ebc,tamanho,r,profilesPathA);

		//getTrainingSet(iteration);
		//		System.out.println(trainingInstances.size() + "  ----- " +temp);
		//		trainingInstances.deleteAttributeAt(5);
		//		trainingInstances.delete(5);
		//		for (int i = 0; i < trainingInstances.numAttributes()-1 ; i++) {
		//			System.out.print("xxx---" + trainingInstances.get(0).value(i) +" "); 
		//		}
		System.out.println();
		for (int i = 0; i < classifiers.length; i++) {
			System.out.println("\n\nClassifier id\t:\t" + i);
			initializeDataStructures();


			long startingTime = System.currentTimeMillis();
			classifiers[i].buildClassifier(trainingInstances);
			applyClassifier(classifiers[i]);
			//	System.out.println("count ---> "+ getCount());
			double overheadTime = System.currentTimeMillis()-startingTime;
			System.out.println("CL"+i+" Overhead time\t:\t" + overheadTime);
			overheadTimes[i].add(overheadTime);
			//System.out.println("----------" +getCount());
			//commented out for faster experiments
			//use when measuring resolution time
			long comparisonsTime = 0;//ebc.comparisonExecution(newBlocks);
			System.out.println("CL"+i+" Classification time\t:\t" + (comparisonsTime+overheadTime));
			resolutionTimes[i].add(new Double(comparisonsTime+overheadTime));

			processComparisons(i, iteration, writer1, writer2,writer3, writer4,th);
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
		attributes.add(new Attribute("similarity"));
		
		classLabels = new ArrayList<String>();
		classLabels.add(NON_MATCH);
		classLabels.add(MATCH);

		classAttribute = new Attribute("class", classLabels);
		attributes.add(classAttribute);
		noOfAttributes = attributes.size();
	}

	private void getStatistics() {
		noOfBlocks = blocks.size();
		validComparisons = 0;
		int noOfEntities = entityIndex.getNoOfEntities();

		redundantCPE = new double[noOfEntities];
		nonRedundantCPE = new double[noOfEntities];
		comparisonsPerBlock = new double[(int)(blocks.size() + 1)];
		for (AbstractBlock block : blocks) {
			comparisonsPerBlock[block.getBlockIndex()] = block.getNoOfComparisons();

			ComparisonIterator iterator = block.getComparisonIterator();
			while (iterator.hasNext()) {
				Comparison comparison = iterator.next();

				int entityId2 = comparison.getEntityId2()+entityIndex.getDatasetLimit();
				redundantCPE[comparison.getEntityId1()]++;
				redundantCPE[entityId2]++;

				if (!entityIndex.isRepeated(block.getBlockIndex(), comparison)) {
					validComparisons++;
					nonRedundantCPE[comparison.getEntityId1()]++;
					nonRedundantCPE[entityId2]++;
				}
			}
		}
	}

	protected Instance getFeatures(int match, List<Integer> commonBlockIndices, Comparison comparison, double flag) {
		double[] instanceValues =null;
		instanceValues = new double[noOfAttributes];

		int entityId2 = comparison.getEntityId2() + entityIndex.getDatasetLimit();
		//	System.out.println(noOfBlocks +"   "+ entityIndex.getNoOfEntityBlocks(comparison.getEntityId1(), 0));
		double ibf1 = Math.log(noOfBlocks/entityIndex.getNoOfEntityBlocks(comparison.getEntityId1(), 0));
		double ibf2 = Math.log(noOfBlocks/entityIndex.getNoOfEntityBlocks(comparison.getEntityId2(), 1));
		try{
			instanceValues[0] = commonBlockIndices.size()*ibf1*ibf2;	
		}catch (Exception e ){
			System.out.println(e.getMessage());
		}
		//CF 	IBF -RACCB 	Jaccard	Sim	,Node Degree

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

		//instanceValues[5] =ebc.getSimilarityAttribute(comparison.getEntityId1(), comparison.getEntityId2());
		//if(flag==1){
			instanceValues[5]=ebc.getSimilarityAttribute(comparison.getEntityId1(), comparison.getEntityId2());
			//instanceValues[5] =ProfileComparison.getJaccardSimilarity(ebc.exportEntityA(comparison.getEntityId1()), ebc.exportEntityB(comparison.getEntityId2()));
		//}
		//else		
			//			if(instanceValues[0]>50)
			//				instanceValues[5]=ProfileComparison.getJaccardSimilarity(ebc.exportEntityA(comparison.getEntityId1()), ebc.exportEntityB(comparison.getEntityId2()));
			//			else
		//instanceValues[5] =0.0;
		instanceValues[6] = match;

		Instance newInstance = new DenseInstance(1.0, instanceValues);
//		if(flag==0){
//			newInstance.deleteAttributeAt(5);
//			//			for (int i = 0; i < newInstance.numAttributes()-1 ; i++) {
//			//			System.out.print(newInstance.value(i) +" "); 
//			//			}
//			//			System.out.println();
//		}
		newInstance.setDataset(trainingInstances);
		return newInstance;
	}




	protected void getTrainingSet_original(int iteration, ExecuteBlockComparisons ebc, int tamanho, int r, String profilesPathA) throws FileNotFoundException {

		sampleMatches.clear();
		sampleNonMatches.clear();
		sampleNonMatchesNotUsed.clear();
		int trueMetadata=0;
		int matchingInstances = (int) (SAMPLE_SIZE*duplicates.size());
		double nonMatchRatio = matchingInstances / (validComparisons - duplicates.size());
		System.out.println("nonMatchRatio --> " + nonMatchRatio  + " duplicates.size() "+ duplicates.size() + " validComparisons " + validComparisons);
		trainingSet = new HashSet<Comparison>(4*matchingInstances);
		trainingInstances = new Instances("trainingSet", attributes, 2*matchingInstances);
		trainingInstances.setClassIndex(noOfAttributes - 1);
		Random random= new Random(iteration);
		PrintStream pstxt = null;
		PrintStream psarff = null;

		if(true){
			try {
				pstxt = new PrintStream(new FileOutputStream(new File("/tmp/levels_arff"+profilesPathA+".txt"),false));
				//pstxt = new PrintStream(new FileOutputStream(new File("/tmp/final_treina.txt"),false));
				psarff = new PrintStream(new FileOutputStream(new File("/tmp/levels_arff"+profilesPathA+".arff"),false));
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
			}
			System.out.println("linha 251");
			psarff.println("@relation whatever");
			for (int i = 0; i < trainingInstances.numAttributes()-1 ; i++) {
				psarff.println("@attribute "+i+" numeric");			
			}		
			psarff.println("@attribute classe {0,1}");
			psarff.println("@data");
			Comparison comparison;


			int level=0;
			Double valor=0.0;
			//System.out.println("primeiroBlock[controle] -->> " + primeiroBlock[controle]);
			for (int i=0;i<blocks.size();i++) {
				ComparisonIterator iterator = blocks.get(i).getComparisonIterator();

				while (iterator.hasNext()) {
					comparison = iterator.next();

					final List<Integer> commonBlockIndices = entityIndex.getCommonBlockIndices(blocks.get(i).getBlockIndex(), comparison);
					if (commonBlockIndices == null) {
						continue;
					}

					double ibf1 = Math.log(noOfBlocks/entityIndex.getNoOfEntityBlocks(comparison.getEntityId1(), 0));
					double ibf2 = Math.log(noOfBlocks/entityIndex.getNoOfEntityBlocks(comparison.getEntityId2(), 1));
					try{
						valor = commonBlockIndices.size()*ibf1*ibf2;	
						
					}catch (Exception e ){
						System.out.println(e.getMessage());
					}
//										try{
//										while(!(valor>=vector[level][0] && valor<=vector[level][1]) )
//											level++;
//										}catch(Exception e){
//											e.getMessage();
//											System.out.println("saindo.....");
//											return;
//										}
					if(valor>1000)
						level=global_threshold+50;
					else
						level=(int) Math.floor(valor/global_threshold);
					int temp=random.nextInt(Nblocks[level][0]);
					if(temp> tamanho)
						continue;
					

					//																										int match = NON_DUPLICATE; // false
					//																										
					//																										if (areMatching(comparison)) {
					//																											if (random.nextDouble() < SAMPLE_SIZE) {
					//																												
					//																												match = DUPLICATE; // true
					//																											} else {
					//																												continue;
					//																											}
					//																										} else if (nonMatchRatio <= random.nextDouble()) {
					//																											continue;
					//																										}

					getLevels(comparison,ebc,blocks.get(i).getBlockIndex(),pstxt,psarff, nonMatchRatio, tamanho,level,blocks.get(i).getNoOfComparisons());
				}
			}
			pstxt.close();
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
			System.out.println("trainingSet.size() - trueMetadata)--->" + (trainingSet.size() - trueMetadata)  + "   ----------->> " + trueMetadata);
		}

	}

	protected void getTrainingSet(int iteration) {
		int trueMetadata = 0;
		Random random = new Random(iteration);
		int matchingInstances = (int) (SAMPLE_SIZE*duplicates.size()+1);
		double nonMatchRatio = matchingInstances / (validComparisons - duplicates.size());

		trainingSet = new HashSet<Comparison>(4*matchingInstances);
		trainingInstances = new Instances("trainingSet", attributes, 2*matchingInstances);
		trainingInstances.setClassIndex(noOfAttributes - 1);

		for (AbstractBlock block : blocks) {
			ComparisonIterator iterator = block.getComparisonIterator();
			while (iterator.hasNext()) {
				Comparison comparison = iterator.next();
				final List<Integer> commonBlockIndices = entityIndex.getCommonBlockIndices(block.getBlockIndex(), comparison);
				if (commonBlockIndices == null) {
					continue;
				}

				int match = NON_DUPLICATE; // false
				if (areMatching(comparison)) {
					if (random.nextDouble() < SAMPLE_SIZE) {
						trueMetadata++;
						match = DUPLICATE; // true
					} else {
						continue;
					}
				} else if (nonMatchRatio <= random.nextDouble()) {
					continue;
				}

				trainingSet.add(comparison);
				Instance newInstance = getFeatures(match, commonBlockIndices, comparison, nonMatchRatio);
				trainingInstances.add(newInstance);
			}
		}
		System.out.println("  trainingSet.size() - trueMetadata)  " + (trainingSet.size() - trueMetadata) + "   ---"+ trueMetadata );
		sampleMatches.add((double) trueMetadata);
		sampleNonMatches.add((double) (trainingSet.size() - trueMetadata));
		sampleNonMatchesNotUsed.add(0.0);
	}

	private void loadFileTrainingSet() throws Exception {
		// TODO Auto-generated method stub
		BufferedReader alac_result = new BufferedReader(new FileReader("/tmp/final_treina.arff"));
		//BufferedReader alac_result = new BufferedReader(new FileReader("/tmp/levels_arffdataset1_imdb.arff"));
		Instances data = new Instances(alac_result);
		data.setClassIndex(data.numAttributes() -1);
		int countP=0,countN=0, countDesc=0;
		double positivos=0.0, negativos=0.0;
		int histograma[][]=new int[11][2];
		double lposit=1.0;
		for (Instance instance : data) {
			if((instance.value(data.numAttributes() -1))==1){
				positivos+=instance.value(data.numAttributes() -2);
			}
			else{
				negativos+=instance.value(data.numAttributes() -2);
				System.out.println(instance.value(data.numAttributes() -2));
			}

			if((instance.value(data.numAttributes() -1))==1.0)  
				countP++;
			else
				countN++;

		}
		//		for (int i = 0; i < histograma.length; i++) {
		//			System.out.println("hist "+ histograma[i][0] +"  "+ histograma[i][1]);
		//		}

		//	double limiar =Math.floor(menorP*10);
		//System.out.println("positivos --> " +lposit);//Math.ceil(a / 100.0)
		//th=Math.ceil((negativos/countN)*10)/10;
		th =(double) new BigDecimal((negativos/countN)).setScale(1, RoundingMode.HALF_UP).floatValue();
		System.out.println(" media " + th);
		//th=0.1;
		//		if(set.contains("dblp"))
		//	th-=0.1;
		//if(set.contains("dblp"))
		//	th=0.2;
		for (Instance instance : data) {
			if((instance.value(data.numAttributes() -1)==0.0) && (instance.value(instance.numAttributes()-2)>=th) )
			{				
				countDesc++;
				System.out.println("descartando.........." + instance.value(instance.numAttributes()-2));


				//instance.setValue(5, 0.0);
				//				for (int i = 0; i < instance.numAttributes()-1 ; i++) {
				//					System.out.print(instance.value(i) +" "); 
				//				}
				//				System.out.println();
				continue;
			}		
			//if((instance.value(data.numAttributes() -1)==0.0))
			//	System.out.println(instance.value(instance.numAttributes()-2)  + "  "+ ebcX.temp_limiar);
			if((instance.value(data.numAttributes() -1)==0.0)){
				for (int i = 0; i < instance.numAttributes()-1 ; i++) {
					System.out.print(instance.value(i) +" "); 
				}
				System.out.println();
			}
			//	instance.setValue(5, 0.0);
			trainingInstances.add(instance);

		}

		System.out.println("valores  --> Positio -> " +countP  +"  negativos -> "+(countN) + "   countDesc -->"+countDesc);
		sampleMatches.add((double) countP);///positivos
		sampleNonMatches.add((double) (countN)); //negativos
		sampleNonMatchesNotUsed.add((double) (countDesc)); //negativos
	}


	private int[][] conta_niveis_hash(List<AbstractBlock> blocks, ExecuteBlockComparisons ebc) {

		int[][] blockSize=  new int[100][3];
		for (int i = 0; i < 100; i++) {
			blockSize[i][0]=0;
			blockSize[i][1]=0;
			blockSize[i][2]=0;
			//	primeiroBlock[i]=0;
		}
		double sim=0.0;


		for ( AbstractBlock b:blocks) {
			ComparisonIterator iterator = b.getComparisonIterator();
			Comparison c;
			while(iterator.hasNext()){			
				c= iterator.next(); 

				final List<Integer> commonBlockIndices = entityIndex.getCommonBlockIndices(b.getBlockIndex(), c);
				if (commonBlockIndices == null) {
					continue;
				}

				double ibf1 = Math.log(noOfBlocks/entityIndex.getNoOfEntityBlocks(c.getEntityId1(), 0));
				double ibf2 = Math.log(noOfBlocks/entityIndex.getNoOfEntityBlocks(c.getEntityId2(), 1));
				try{
					sim = commonBlockIndices.size()*ibf1*ibf2;	
				}catch (Exception e ){
					System.out.println(e.getMessage());
				}
				int level=0;
				if(sim>1000){
					level=((int)Math.floor(global_threshold+50));
				}else
					level=((int)Math.floor(sim/global_threshold));

				blockSize[level][0]++;
				if(areMatching(c))
					blockSize[level][2]++;
				else
					blockSize[level][1]++;

			}
		}
		for (int i = 0; i < 100; i++) {
			if(blockSize[i][0] !=0)
				//	perc[i]=(((double)tamanho)/(blockSize[i]));
				System.out.println(i + " tamanho do bloco "+  "  " + blockSize[i][0] + " " +  blockSize[i][1]  +"  "+ blockSize[i][2]);
			//totalPares += blockHash.blockSize[i];
		}
		return blockSize;



	}

	private int[][] conta_niveis_TUBE(List<AbstractBlock> blocks, ExecuteBlockComparisons ebc) throws IOException {

		int[][] blockSize=  new int[100][3];
		for (int i = 0; i < 100; i++) {
			blockSize[i][0]=0;
			blockSize[i][1]=0;
			blockSize[i][2]=0;
			//	primeiroBlock[i]=0;
		}
		double sim=0.0;
		BufferedWriter writer1;

		writer1 = new BufferedWriter(new FileWriter("/tmp/attributo.arff"));
		writer1.append("@relation documents \n @attribute F1  numeric \n @attribute classe {0,1} \n  @data \n");

		for ( AbstractBlock b:blocks) {
			ComparisonIterator iterator = b.getComparisonIterator();
			Comparison c;
			while(iterator.hasNext()){			
				c= iterator.next(); 

				final List<Integer> commonBlockIndices = entityIndex.getCommonBlockIndices(b.getBlockIndex(), c);
				if (commonBlockIndices == null) {
					continue;
				}

				double ibf1 = Math.log(noOfBlocks/entityIndex.getNoOfEntityBlocks(c.getEntityId1(), 0));
				double ibf2 = Math.log(noOfBlocks/entityIndex.getNoOfEntityBlocks(c.getEntityId2(), 1));
				try{
					sim = commonBlockIndices.size()*ibf1*ibf2;	
				}catch (Exception e ){
					System.out.println(e.getMessage());
				}

				writer1.append(sim+ ", " + (areMatching(c)==true?1:0)+ "\n");

			}
		}
		writer1.close();
		//blockSize=callGeraBinsTUBE();
		blockSize=callGeraBinsTUBE2();
		//		for (int i = 0; i < 100; i++) {
		//			if(blockSize[i][0] !=0)
		//				//	perc[i]=(((double)tamanho)/(blockSize[i]));
		//				System.out.println(i + " tamanho do bloco "+  "  " + blockSize[i][0] + " " +  blockSize[i][1]  +"  "+ blockSize[i][2]);
		//			//totalPares += blockHash.blockSize[i];
		//		}
		return blockSize;

	}


	private int[][] callGeraBinsTUBE() throws IOException {
		String line;
		String cmd;
		String userHome = System.getProperty("user.home");

		int instances[][]=new int[30][2];
		Process proc = null;		
		BufferedReader read, buf;
		//java -Xmx1024m -classpath ../../TUBE/src/ weka.estimators.EqualWidthEstimator -i /tmp/attributo.arff -Y -Z -B 20 -V 4

		//	new CriaMatrixWeka(common).criaArffActiveLearning("/tmp/levels.txt",2);
		cmd = "cd  "+ userHome+ "/Downloads/SSARP/Dedup/test5/; java -Xmx1024m -classpath ../../TUBE/src/  weka.estimators.EqualWidthEstimator -i /tmp/attributo.arff -B 20 -V 4";
		proc = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", cmd});
		System.out.println(cmd);
		read = new BufferedReader(new InputStreamReader(proc.getInputStream()));
		try {
			proc.waitFor();
		} catch (InterruptedException e) {
			System.out.println(e.getMessage());
		}
		
		buf = new BufferedReader(new InputStreamReader(proc.getInputStream()));
		String st[];
		int i=0;
		while ((line=buf.readLine())!=null) {
			System.out.println(line);
			st=line.split("\\|");
			System.out.println("st  "+ st[0]);
			if(st[0].contains(": #")){

				String lixo=st[4].toString().trim();
				instances[i][0]=Integer.parseInt(lixo);
				String temp=st[1].toString().replaceAll("\\[", "").replaceAll("[()]", "").replaceAll("\\]", "");;
				vector[i][0]=Double.parseDouble(temp.split(",")[0].trim());
				vector[i][1]=Double.parseDouble(temp.split(",")[1].trim());
				System.out.println("level " + i +" instances ->" + instances[i][0]);
				i++;
			}


		}
		vector[--i][1]++;
		vector[0][0]--;
		//		while (read.ready()) {
		//			System.out.println(read.readLine());
		//		}
		return instances;
		//read = new BufferedReader("tmp/bins-0LL.hist"
		//		BufferedReader bins= new BufferedReader(new FileReader("/tmp/bins-0LL.hist"));
		//		String st;
		//		int i=0,j=1;
		//		vector[0][0]=0.0;
		//		while((st=bins.readLine())!=null){
		//			System.out.println(st);
		//			vector[i][j]= Double.parseDouble(st.split(" ")[0]);
		//			if(++j==2){
		//				j=0;
		//				i++;
		//			}
		//		}

	}
	
	private int[][] callGeraBinsTUBE2() throws IOException {
		String line;
		String cmd;
		String userHome = System.getProperty("user.home");

//		int instances[][]=new int[30][2];
		Process proc = null;		
		BufferedReader read, buf = null;
		//java -Xmx1024m -classpath ../../TUBE/src/ weka.estimators.EqualWidthEstimator -i /tmp/attributo.arff -Y -Z -B 20 -V 4

		//	new CriaMatrixWeka(common).criaArffActiveLearning("/tmp/levels.txt",2);
		cmd = "cd  "+ userHome+ "/Downloads/weka-3-8-1/weka/; java weka.filters.unsupervised.attribute.Discretize -O -B 20  -R first-last -i /tmp/attributo.arff  -o /tmp/lixo";
		proc = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", cmd});
		System.out.println(cmd);
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
		
		String temp;
		//buf.close();
		int flag=0,i=0;
		int instances[][]=new int[50][2];
		read = new  BufferedReader(new FileReader("/tmp/lixo"));
		while((temp=read.readLine())!=null){
			if(flag++<2)
				continue;
			if(flag==3){
				String[] splitString = temp.replaceAll("'|\\(|]|\\)|}|'","").replaceAll("inf","0.0").replaceFirst("-","").trim().split("\\{")[1].split(",");;
	            //System.out.println(splitString.length);// should be 14
	            for (String string : splitString) {
	                try{    
	                	string=string.replace("\\","");
	                	vector[i][0]= Double.parseDouble(string.split("-")[0]);
	            		vector[i++][1]= Double.parseDouble((string.split("-")[1]));
	                }catch(Exception e){
	            		System.out.println(string);
	            	}
	            }
			}
		}
		vector[i--][1]=20000;
		read.close();
		read = new  BufferedReader(new FileReader("/tmp/attributo.arff"));
		int level=0;
		while((temp=read.readLine())!=null){
			level=0;
			if(temp.contains("@"))
				continue;
			Double valor= Double.parseDouble(temp.split(",")[0]);
			try{
			while(!(valor>=vector[level][0] && valor<=vector[level][1]) )
				if(level++>=50)
					break;
			}catch(Exception e){
				System.out.println(valor );
			}
			instances[level][0]++;
			if(temp.split(",")[1].equals("1"))
				instances[level][1]++;
				
			
		}
		
		
		for (int j = 0; j < instances.length; j++) {
			System.out.println("j " + j + " instance "+ instances[j][0] +" pos " + instances[j][1]);
		}
		
		
		//		while (read.ready()) {
		//			System.out.println(read.readLine());
		//		}
		return instances;
		//read = new BufferedReader("tmp/bins-0LL.hist"
		//		BufferedReader bins= new BufferedReader(new FileReader("/tmp/bins-0LL.hist"));
		//		String st;
		//		int i=0,j=1;
		//		vector[0][0]=0.0;
		//		while((st=bins.readLine())!=null){
		//			System.out.println(st);
		//			vector[i][j]= Double.parseDouble(st.split(" ")[0]);
		//			if(++j==2){
		//				j=0;
		//				i++;
		//			}
		//		}

	}


	private void callGeraBins() throws IOException {
		String line;
		String cmd;
		String userHome = System.getProperty("user.home");
		String file ="/tmp/levels_arff"+set+ " /tmp/teste";
		int att=noOfAttributes-1;
		Process proc = null;		
		BufferedReader read, buf;
		//	new CriaMatrixWeka(common).criaArffActiveLearning("/tmp/levels.txt",2);
		cmd = "cd  "+ userHome+ "/Downloads/SSARP/Dedup/test5/; bash ./gera_beans.sh  " +file + "   "+ att + " "+ att;
		proc = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", cmd});
		System.out.println(cmd);
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
		int att=noOfAttributes-1;
		Process proc = null;		
		BufferedReader read, buf;
		cmd = "cd  "+ userHome+ "/Downloads/SSARP/Dedup/test5/; bash ./SSARP2.sh  " +file + " "+ i +" " +att;
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



	private int getLevels(Comparison comparison, ExecuteBlockComparisons ebc, int i, PrintStream pstxt, PrintStream psarff, double nonMatchRatio, double tamanho, int controle, double d) throws FileNotFoundException {
		//		String concatStringA;
		//		String concatStringB;
		//		Double sim=comparison.sim;

		//
		//		Set<DataStructures.Attribute> setAtributtes = ebc.exportEntityA(comparison.getEntityId1());
		//		String sA[]=Converter.createVector(setAtributtes,comparison.getEntityId1(),Converter.atributos_valueA);
		//		concatStringA=sA[0]+"::";////title,

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
		//		//System.out.println(concatStringA);
		//		setAtributtes = ebc.exportEntityB(comparison.getEntityId2());
		//		String sB[]=Converter.createVector(setAtributtes,comparison.getEntityId2(),Converter.atributos_valueB);
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
		//System.out.println(concatStringB);
		String label="false";
		IdDuplicates duplicatePair1 = new IdDuplicates(comparison.getEntityId1(), comparison.getEntityId2());
		if (duplicates.contains(duplicatePair1)) {
			label="true";
			//System.out.println("duplicate pair " + concatStringA + "   "+ concatStringB);
		}
		//////////////
		//System.out.println(comparison.sim);


		//
		//double similarity = ProfileComparison.getJaccardSimilarity(ebc.exportEntityA(comparison.getEntityId1()), ebc.exportEntityB(comparison.getEntityId2()));
		final List<Integer> commonBlockIndices = entityIndex.getCommonBlockIndices(i, comparison);
		Instance newInstanceTemp = getFeatures(label.contains("true")?1:0, commonBlockIndices, comparison,1.0);

		DecimalFormat decimalFormatter = new DecimalFormat("############.#####");
		String temp;

		for (int j = 0; j < newInstanceTemp.numAttributes()-1; j++) {

			temp = decimalFormatter.format(newInstanceTemp.value(j));
			temp=temp.replace(",", ".");
			//System.out.println(temp);
			//psarff.print(newInstanceTemp.value(j) + ", ");
			psarff.print(temp + ", ");
		}
		//psarff.print(d+", ");
		//psarff_level[controle].print(d +", ");
		psarff.println(label.contains("true")?1:0);
		//psarff_level[controle].println(label.contains("true")?1:0);
		///////////    	
		//FileUtilities.save_data_db( String.valueOf(i), sB[0],concatStringA,concatStringB, sim,label,null,pstxt,pstxt_level, psarff_level,k  );
		//	pstxt.println(concatStringA +","+sim+ ", " +concatStringB+ ","+label+ " ,"+String.valueOf(i));
		//				pstxt_level[controle].println(concatStringA +","+sim+ ", " +concatStringB+ ","+label+ " ,"+String.valueOf(i));
		//				pstxt_level[controle].flush();
		//	pstxt.flush();
		//	if(ele)
		//	elements[controle]++;
		//System.out.println("controle " + controle + " --> element "+elements[controle]);
		return -1;
	}


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


	public void printStatistics() throws IOException {
		System.out.println("\n\n\n\n\n+++++++++++++++++++++++Printing overall statistics+++++++++++++++++++++++");

		double avSMatches = StatisticsUtilities.getMeanValue(sampleMatches);
		double avSNonMatches = StatisticsUtilities.getMeanValue(sampleNonMatches);
		System.out.println("Sample matches\t:\t" + avSMatches + "+-" + StatisticsUtilities.getStandardDeviation(avSMatches, sampleMatches));
		System.out.println("Sample non-matches\t:\t" + avSNonMatches + "+-" + StatisticsUtilities.getStandardDeviation(avSNonMatches, sampleNonMatches));

		for (int i = 0; i < overheadTimes.length; i++) {
			System.out.println("\n\n\n\n\nClassifier id\t:\t" + (i));

			double avOTime = StatisticsUtilities.getMeanValue(overheadTimes[i]);
			double avRTime = StatisticsUtilities.getMeanValue(resolutionTimes[i]);
			double avSEComparisons = StatisticsUtilities.getMeanValue(sampleComparisons[i]);
			double avSDuplicates = StatisticsUtilities.getMeanValue(sampleDuplicates[i]);



			final List<Double> pcs = new ArrayList<Double>();
			for (int j = 0; j < sampleMatches.size(); j++) {
				//pcs.add(sampleDuplicates[i].get(j)/(duplicates.size() - sampleMatches.get(j))*100.0);
				System.out.println(sampleDuplicates[i].get(j) + "   "+  (duplicates.size()  +"  "+ sampleMatches.get(j)));
				pcs.add((sampleDuplicates[i].get(j))/(duplicates.size())*100.0);
			}
			double avSPC = StatisticsUtilities.getMeanValue(pcs);

			System.out.println("Overhead time\t:\t" + avOTime + "+-" + StatisticsUtilities.getStandardDeviation(avOTime, overheadTimes[i]));
			System.out.println("Resolution time\t:\t" + avRTime + "+-" + StatisticsUtilities.getStandardDeviation(avRTime, resolutionTimes[i]));
			System.out.println("Sample duplicates\t:\t" + avSDuplicates + "+-" + StatisticsUtilities.getStandardDeviation(avSDuplicates, sampleDuplicates[i]));
			System.out.println("Sample PC\t:\t" + avSPC  + "+-  " + + StatisticsUtilities.getStandardDeviation(avSPC  , pcs));
			System.out.println("Sample comparisons\t:\t " + avSEComparisons + "+- " + StatisticsUtilities.getStandardDeviation(avSEComparisons, sampleComparisons[i]));

			//			try {
			//				writer.write(" " +avOTime+ " "+ StatisticsUtilities.getStandardDeviation(avOTime, overheadTimes[i]));
			//				writer.write(" " +avRTime + " " + StatisticsUtilities.getStandardDeviation(avRTime, resolutionTimes[i]));
			//				writer.write(" " +avSDuplicates + " " + StatisticsUtilities.getStandardDeviation(avSDuplicates, sampleDuplicates[i]));
			//				writer.write(" " +avSPC  + "  " + + StatisticsUtilities.getStandardDeviation(avSPC  , pcs));
			//				writer.write(" " +avSEComparisons + " " + StatisticsUtilities.getStandardDeviation(avSEComparisons, sampleComparisons[i]));
			//			} catch (IOException e) {
			//				// TODO Auto-generated catch block
			//				e.printStackTrace();
			//			}


		}
	}
}