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
import java.io.PrintWriter;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.imageio.stream.MemoryCacheImageOutputStream;

import DataStructures.AbstractBlock;
import DataStructures.Comparison;
import DataStructures.EntityIndex;
import DataStructures.IdDuplicates;
import DataStructures.UnilateralBlock;
import Utilities.ArrayComparator;
import Utilities.ComparisonIterator;
import Utilities.Constants;
import Utilities.Converter;
import Utilities.ExecuteBlockComparisons;
import Utilities.ProfileComparison;
import Utilities.StatisticsUtilities;
import Utilities.kmeans;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.RandomForest;
import weka.classifiers.trees.RandomTree;
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
	//protected int[] Nblocks;

	protected Attribute classAttribute;
	protected ArrayList<Attribute> attributes;
	protected final EntityIndex entityIndex;
	protected Instances trainingInstances;
	// List<Class1> list = new ArrayList<Class1>();
	protected List<AbstractBlock> blocks;
	protected List<Double>[] overheadTimes;
	protected List<Double>[] resolutionTimes;
	protected List<Double> sampleMatches;
	protected List<Double> sampleNonMatches;
	protected List<Double>[] sampleComparisons;
	protected List<Double>[] sampleDuplicates;
	protected List<String> classLabels;
	protected final Set<IdDuplicates> duplicates;
	protected Set<Comparison> trainingSet;
	protected Set<IdDuplicates> detectedDuplicates;
	protected int totalPares=0;
	protected int elements[];
	protected Hashtable balance = new Hashtable();
	protected final String names[]=(new Converter()).atributos_value;
	int Nblocks[];
	ExecuteBlockComparisons ebcX;
	String set="";
	
	public AbstractSupervisedMetablocking (int classifiers, List<AbstractBlock> bls, Set<IdDuplicates> duplicatePairs, ExecuteBlockComparisons ebc) {
		blocks = bls;
		dirtyER = blocks.get(0) instanceof UnilateralBlock;
		entityIndex = new EntityIndex(blocks);
		duplicates = duplicatePairs;
		noOfClassifiers = classifiers;

		getStatistics();
		prepareStatistics();
		getAttributes();
		Nblocks=conta_niveis_hash(blocks,ebc);
	}

	protected abstract void applyClassifier(Classifier classifier) throws Exception;
	protected abstract List<AbstractBlock> gatherComparisons();
	protected abstract void initializeDataStructures();
	protected abstract void processComparisons(int configurationId, int iteration, BufferedWriter writer, BufferedWriter writer2, BufferedWriter writer3,BufferedWriter writer4);
	protected abstract void savePairs(int i, ExecuteBlockComparisons ebc);
	protected abstract int getCount();


	public void applyProcessing(int iteration, Classifier[] classifiers, ExecuteBlockComparisons ebc, int tamanho, BufferedWriter writer1, BufferedWriter writer2, BufferedWriter writer3, BufferedWriter writer4, int r, String profilesPathA) throws Exception {
		elements=new int[10];

		ebcX=ebc;
		set=profilesPathA;
		getTrainingSet_original(iteration,ebc,tamanho,r,profilesPathA);

		//getTrainingSet(iteration,ebc,tamanho);
		System.out.println(trainingInstances.size() + "  ----- " +temp);

		for (int i = 0; i < classifiers.length; i++) {
			System.out.println("\n\nClassifier id\t:\t" + i);
			initializeDataStructures();


			long startingTime = System.currentTimeMillis();
			classifiers[i].buildClassifier(trainingInstances);
			applyClassifier(classifiers[i]);
			//	System.out.println("count ---> "+ getCount());
			List<AbstractBlock> newBlocks = gatherComparisons();
			double overheadTime = System.currentTimeMillis()-startingTime;
			System.out.println("CL"+i+" Overhead time\t:\t" + overheadTime);
			overheadTimes[i].add(overheadTime);
			//System.out.println("----------" +getCount());
			//commented out for faster experiments
			//use when measuring resolution time
			long comparisonsTime = 0;//ebc.comparisonExecution(newBlocks);
			System.out.println("CL"+i+" Classification time\t:\t" + (comparisonsTime+overheadTime));
			resolutionTimes[i].add(new Double(comparisonsTime+overheadTime));

			processComparisons(i, iteration, writer1, writer2,writer3, writer4);
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
	//	attributes.add(new Attribute("teste1"));
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
		if(flag==0.0)
			instanceValues = new double[noOfAttributes-1];
		else
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
		//	ProfileComparison.getJaccardSimilarity(profiles1[comparison.getEntityId1()].getAttributes(), profiles2[comparison.getEntityId2()].getAttributes());
		instanceValues[2] = commonBlockIndices.size() / (redundantCPE[comparison.getEntityId1()] + redundantCPE[entityId2] - commonBlockIndices.size());
		instanceValues[3] = nonRedundantCPE[comparison.getEntityId1()];
		instanceValues[4] = nonRedundantCPE[entityId2];;
		//instanceValues[5] = entityIndex.getNoOfEntityBlocks(comparison.getEntityId1(), 0);
		//instanceValues[6] = entityIndex.getNoOfEntityBlocks(comparison.getEntityId2(), 1);
//		
		if(flag==1.0){
			instanceValues[5] =ProfileComparison.getJaccardSimilarity(ebcX.exportEntityA(comparison.getEntityId1()), ebcX.exportEntityB(comparison.getEntityId2()));
			instanceValues[6] = match;
			//instanceValues.
		}
		else {
			instanceValues[5] = match;
		}
		 //ebcX.getSImilarityAttribute(comparison.getEntityId1(),comparison.getEntityId2(),names);
				
		
		
		//instanceValues[6] = match;

		Instance newInstance = new DenseInstance(1.0, instanceValues);
		newInstance.setDataset(trainingInstances);
		return newInstance;
	}



	int temp=0;
	protected void getTrainingSet_original(int iteration, ExecuteBlockComparisons ebc, int tamanho, int r, String profilesPathA) throws FileNotFoundException {
		
		sampleMatches.clear();
		sampleNonMatches.clear();

		int trueMetadata=0;
		int matchingInstances = (int) (SAMPLE_SIZE*duplicates.size());
		double nonMatchRatio = matchingInstances / (validComparisons - duplicates.size());
		System.out.println("nonMatchRatio --> " + nonMatchRatio  + " duplicates.size() "+ duplicates.size() + " validComparisons " + validComparisons);
		trainingSet = new HashSet<Comparison>(4*matchingInstances);
		trainingInstances = new Instances("trainingSet", attributes, 2*matchingInstances);
		trainingInstances.setClassIndex(noOfAttributes - 1);
		//double  vector[]={0,0,0,0,0};
		Random random= new Random(iteration);
		PrintStream pstxt = null;
		PrintStream psarff = null;

if(true){
			//encontraPares();

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
			//Vector<Comparison> randomInstances= new Vector<Comparison>(4*matchingInstances);;
			Comparison comparison;

			System.out.println("linha 260");

							Collections.sort(blocks, new Comparator<AbstractBlock>() {
								public int compare(AbstractBlock c1, AbstractBlock c2) {
									if (c1.getNoOfComparisons() < c2.getNoOfComparisons()) return -1;
									if (c1.getNoOfComparisons() > c2.getNoOfComparisons()) return 1;
									return 0;
								}});
			//Collections.shuffle(blocks);

			//
			long startingTime = System.currentTimeMillis();

			long deltaTime= System.currentTimeMillis()-startingTime;

			System.out.println("time da contagem "+ deltaTime);

			int controle=-1;
			PrintStream pstxt_level[] = new PrintStream[10];
			PrintStream psarff_level[]= new PrintStream[10];
			int j=1,l=0;
			int retorno=-1;
			int tentativas=0;
			int pos=0,neg=0;
			//HashMap<Integer, ArrayList<DataStructures.Comparison>> deep= blockHash.deep;

			//int valores[]=new int[tamanho];
			for (int i = 0; i < 10; i++) {
				try {
					pstxt_level[i] = new PrintStream(new FileOutputStream(new File("/tmp/levels_arff_level"+i+profilesPathA+".txt"),false));
					psarff_level[i] = new PrintStream(new FileOutputStream(new File("/tmp/levels_arff_level"+i+profilesPathA+".arff"),false));
					psarff_level[i].println("@relation whatever");
					for (int k = 0; k < trainingInstances.numAttributes()-1 ; k++) {					
						psarff_level[i].println("@attribute "+k+" numeric");			
					}		
					psarff_level[i].println("@attribute classe {0,1}");
					psarff_level[i].println("@data");
				} catch (FileNotFoundException e1) {
					e1.printStackTrace();
				}

			}

		
				
				j=1;
				l=0;  
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
							level = commonBlockIndices.size();	
						}catch (Exception e ){
							System.out.println(e.getMessage());
						}
//						comparison.sim=ebc.getSImilarityAttribute(comparison.getEntityId1(),comparison.getEntityId2(),names);//ProfileComparison.getJaccardSimilarity(ebcX.exportEntityA(comparison.getEntityId1()), ebcX.exportEntityB(comparison.getEntityId2())); //ebc.getSImilarityAttribute(comparison.getEntityId1(),comparison.getEntityId2(),names);
//						if(comparison.sim>=1.0)
//							comparison.sim=0.99;
//						comparison.sim = ProfileComparison.getJaccardSimilarity(ebc.exportEntityA(comparison.getEntityId1()), ebc.exportEntityB(comparison.getEntityId2()));
//						
//						
						if(level>20)
							level=20;
						else
							level=(int) Math.floor(level);
						{
						//	if(comparison.sim>= ((double)level*0.1) && comparison.sim< ((double)(level+1)*0.1))
							{	
								//if(level>1 && level< 5)
								//	continue;
								int temp=random.nextInt(Nblocks[level]);
								
								
								if(temp> tamanho)
									continue;
							//	System.out.println(level + "   "+ comparison.sim);
//								Instance newInstanceTemp = getFeatures(label.contains("true")?1:0, commonBlockIndices, comparison,comparison.sim);
//								if(level<3 && areMatching(comparison)){
//									//if(random.nextInt(100)<99)
//									//	continue;
//									l++;
//								}else 
//									if(temp> tamanho)
//										continue;
//									
//								
							
//								if(newInstanceTemp.value(0)>100){
//									if(random.nextInt(10)>4)
//										continue;
//								}else	{								
//									if(temp>tamanho)
//									continue;
//								}
//								if(!areMatching(comparison))
//									continue;
//								
//								if (blocks.get(i).getNoOfComparisons()<5 && newInstanceTemp.value(0)>100){ 
//									if(random.nextInt(10)>1)
//										continue;
//									System.out.println(".... " +l++);
//								}else{
//									if(areMatching(comparison)){
//										if(random.nextInt(10)>8)
//											continue;
//									}else
//									if(random.nextInt(853975)>100){
//										continue;
//									}
//							//	}
								
						

//																			int match = NON_DUPLICATE; // false
//																			if (areMatching(comparison)) {
//																				if (random.nextDouble() < SAMPLE_SIZE) {
//																					trueMetadata++;
//																					match = DUPLICATE; // true
//																				} else {
//																					continue;
//																				}
//																			} else if (nonMatchRatio <= random.nextDouble()) {
//																				continue;
//																			}
																		
								//								if(controle==4)
								//									System.out.println("descarte " + temp +"  "+ Nblocks[controle]);
								if((retorno=getLevels(comparison,ebc,blocks.get(i).getBlockIndex(),pstxt,psarff,pstxt_level,psarff_level, nonMatchRatio, tamanho,level,blocks.get(i).getNoOfComparisons()))<=0){
									//if (!areMatching(comparison)) {
									//	temp=temp*2;
									//	break;
									//}
										
								}
								//pstxt_level[level].flush();
								//psarff_level[level].flush();
								//	
							}
						}
					}
				}
			

			System.out.println("tamanho do arquivo arff "+ l);
			pstxt.close();
			psarff.close();
			for (int m = 0; m < 10; m++) {
				pstxt_level[m].close();
				psarff_level[m].close();
			}
//					try {			
//						//loadFileTrainingSet(kmeans.run("/tmp/levels_arff.arff",100, trainingInstances));
//						//trainingInstances=kmeans.run("/tmp/levels_arff.arff",tamanho, trainingInstances,sampleMatches,sampleNonMatches);
//					} catch (Exception e2) {
//						e2.printStackTrace();
//					}
					//System.out.println("training match Instances ---" + sampleMatches.get(0));
					//System.out.println("training match Instances ---" + sampleNonMatches.get(0));
//					for (int k = 0; k < trainingInstances.size(); k++) {
//						for (int k2 = 0; k2 < 6; k2++) {
//							System.out.print( trainingInstances.get(k).value(k2) +"  ");
//						}
//						System.out.println();
//					}	

					try {
						File f = new File("/tmp/lock");
						while(f.exists() ) { 
							System.out.println("sleeping................");
							Thread.sleep(1000);
						}
						f.createNewFile();
						
						callGeraBins();
						for (int i = 8; i >=8; i--) {
							//System.out.println("chamando allac " + i	);
								//	DiscretizeTest.run_short("/tmp/levels_arff_level"+i+".arff", "/tmp/levels_arff_level"+i+"D.arff");			
								//	DiscretizeTest.run("/tmp/levels_arff_level"+i+".arff", "/tmp/levels_arff_level"+i+"D.arff");			
								callAllac(i,r);  
						}
						//teste_tree(trainingInstances);
						//loadFileTrainingSet(trainingInstances);
						loadFileTrainingSet();
						f.delete();
					}  catch (Exception e) {
						e.printStackTrace();
					}
		System.err.println(" ");
		System.out.println("trainingSet.size() - trueMetadata)--->" + (trainingSet.size() - trueMetadata)  + "   ----------->> " + trueMetadata);
		//sampleMatches.add((double) trueMetadata);///positivos
		//	sampleNonMatches.add((double) (trainingSet.size() - trueMetadata)); //negativos
		}

	}

	private void encontraPares() {

		Instance P_menor=new DenseInstance(5);
		for (int i = 0; i < 5; i++) {
			P_menor.setValue(i, 100000000.0);
		}

		for (int i=0;i<blocks.size();i++) {
			ComparisonIterator iterator = blocks.get(i).getComparisonIterator();
			//if(retorno==0)
			//	break;
			//	System.out.println("Nblocks[controle]---->>>>>>>>>>>>>>>>>>" + Nblocks[controle]);
			//System.out.println(blocks.get(i).getBlockIndex());
			while (iterator.hasNext()) {
				Comparison comparison = iterator.next();

				final List<Integer> commonBlockIndices = entityIndex.getCommonBlockIndices(blocks.get(i).getBlockIndex(), comparison);
				if (commonBlockIndices == null) {
					continue;
				}
				Instance newInstanceTemp = getFeatures(0, commonBlockIndices, comparison,comparison.sim);
				if(newInstanceTemp.value(0)<P_menor.value(0)){
					for (int j = 0; j < 5; j++) {
						System.out.print(P_menor.value(j)+ "    ");
						P_menor.setValue(j, newInstanceTemp.value(j));	
					}
					System.out.println();
				}
			}
		}
		for (int i = 0; i < 5; i++) {
			System.out.println("valor " + P_menor.value(i));
		}

	}

	private void loadFileTrainingSet() throws Exception {
		// TODO Auto-generated method stub
		BufferedReader alac_result = new BufferedReader(new FileReader("/tmp/final_treina.arff"));
		//BufferedReader alac_result = new BufferedReader(new FileReader("/tmp/levels_arffdataset1_amazon.arff"));
		Instances data = new Instances(alac_result);
		data.setClassIndex(data.numAttributes() -1);
		int countP=0,countN=0;
		int len=3;
		//trainingInstances.add(data.get(0));
		//ArrayList<Attribute>




		//		
				for (Instance instance : data) {
					if((instance.value(data.numAttributes() -1)==0.0) && (instance.value(5))>0.1){
						//countN++;
						System.out.println("descartando..........");
						continue;
					}
//					
//					for (int j = 0; j < 6; j++) {
//						//instance.setValue(j, 0.5);
//					}
						instance.setMissing(5); //deleteAttributeAt(5);  
						trainingInstances.add(instance);
						if((instance.value(data.numAttributes() -1))==1)  
							countP++;
						else
							countN++;
					//}
					
				}
		//	
//		for (int j = 0; j < trainingInstances.size(); j++) {
//			for (int j2 = 0; j2 < 6; j2++) {
//				System.out.print(trainingInstances.get(j).value(j2)+ " ");
//			}
//			System.out.println();
//		}

		System.out.println("valores  --> Positio -> " +countP  +"  negativos -> "+countN);
		sampleMatches.add((double) countP);///positivos
		sampleNonMatches.add((double) (countN)); //negativos
	}

	private int  valida_pares(int j, double memoria_pares){

		NaiveBayes naiveBayes = new NaiveBayes();
		ArrayList<Integer> retainedEntities1 = new ArrayList<Integer>();
		ArrayList<Integer> retainedEntities2 = new ArrayList<Integer>();
		Set<IdDuplicates> TdetectedDuplicates=  detectedDuplicates = new HashSet<IdDuplicates>(55000);
		int instanceLabel = 0;
		try {
			naiveBayes.buildClassifier(trainingInstances);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("tamanho trainamento " + trainingInstances.size());
		//j48.classifyInstance(instance);


		int countP = 0;
		int countN = 0;
		for (AbstractBlock block : blocks) {
			ComparisonIterator iterator = block.getComparisonIterator();

			while (iterator.hasNext()) {

				Comparison comparison = iterator.next();
				final List<Integer> commonBlockIndices = entityIndex.getCommonBlockIndices(block.getBlockIndex(), comparison);
				if (commonBlockIndices == null) {
					continue;
				}

				if (trainingSet.contains(comparison)) {
					continue;
				}

				Instance currentInstance = getFeatures(NON_DUPLICATE, commonBlockIndices, comparison,0);
				
				try {
					instanceLabel = (int) naiveBayes.classifyInstance(currentInstance);

					if (instanceLabel == DUPLICATE) {
						//count++;
						retainedEntities1.add(comparison.getEntityId1());
						retainedEntities2.add(comparison.getEntityId2());
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
			}
		}

		int[] entityIds1 = Converter.convertCollectionToArray(retainedEntities1);
		int[] entityIds2 = Converter.convertCollectionToArray(retainedEntities2);
		int teste=0;
		for (int i = 0; i < entityIds1.length; i++) {
			//System.out.println(entityIds1[i] +" ---" + entityIds2[i]);
			teste++;
			Comparison comparison = new Comparison(dirtyER, entityIds1[i], entityIds2[i],0.0);
			if (areMatching(comparison)) {
				final IdDuplicates matchingPair = new IdDuplicates(entityIds1[i], entityIds2[i]);
				TdetectedDuplicates.add(matchingPair);  
				//System.out.println("match ->>>>" +entityIds1[i] +" ---" + entityIds2[i]);
			}	      
		}	 
		//System.out.println(" data 2 --->"+data2.size());
		
		int pares =entityIds1.length;
		if(j==0){
			memoria_pares=pares;
			System.out.println("memoria ---> " + memoria_pares);
		}else{
			if((pares-memoria_pares)>10000 || ((pares-memoria_pares)<-1000 && instanceLabel!=DUPLICATE)){
				trainingInstances.remove(trainingInstances.size()-1);
				System.out.println("                             removendo instancia" + (trainingInstances.size()-1) + " ---- "+ (pares-memoria_pares));
				System.out.println();
//				System.out.println("+++++++++++++++++++++++++++++++");
//				valida_pares();
//				System.out.println("+++++++++++++++++++++++++++++++");
			}else
			{
				memoria_pares=pares;
				System.out.println("************Executed comparisons blocking\t:\t" 	);
				System.out.println("************Executed comparisons\t:\t" + entityIds1.length);
				System.out.println("************Detected duplicates\t:\t" + TdetectedDuplicates.size());
				System.out.println("************Detected duplicates\t:\t" + (double)TdetectedDuplicates.size()/8700 + "\n\n\n\n");
			}				
		}	
		retainedEntities1.clear();
		retainedEntities2.clear();
		TdetectedDuplicates.clear();  	
		return (int) memoria_pares;
	}

	private void teste_tree(Instances trainingInstances2) throws Exception {

		BufferedReader inputReader = null;
		ArrayList<Integer> retainedEntities1 = new ArrayList<Integer>();
		ArrayList<Integer> retainedEntities2 = new ArrayList<Integer>();
		Set<IdDuplicates> TdetectedDuplicates=  detectedDuplicates = new HashSet<IdDuplicates>(55000);
		inputReader = new BufferedReader(new FileReader("/tmp/tree.arff"));

		Instances data = new Instances(inputReader);
		data.setClassIndex(data.numAttributes() -1);
		//		
		RandomForest rf = new RandomForest();
		//  rf.setNumTrees(10);
		rf.setMaxDepth(2);

		// rf.buildClassifier(data);
		// rf.
		//   RandomTree rf= new RandomTree();

		Instances data2 = new Instances(data,0);
		data2.setClassIndex(data2.numAttributes() -1);
		int linha=0,linha_arff=0;
		double memoria=1.0;
		double memoriaPares=0.0;
		for (int i = 0; i < 5; i++) {
			data2.add(data.get(i));
			linha++;
			linha_arff++;
		}
		int vector[] = new int[500000];
		//rf.buildClassifier(data);

		for (int j = 0; j < 900;j++) 
		{
			//data2.add(data.get(linha++));
			//linha_arff++;

			rf.buildClassifier(data2);
			System.out.println(data2.size());
			//j48.classifyInstance(instance);

			int countP=0,countN=0;
			int flag=1;
			for (AbstractBlock block : blocks) {
				ComparisonIterator iterator = block.getComparisonIterator();

				while (iterator.hasNext()) {

					Comparison comparison = iterator.next();
					final List<Integer> commonBlockIndices = entityIndex.getCommonBlockIndices(block.getBlockIndex(), comparison);
					if (commonBlockIndices == null) {
						continue;
					}

					if (trainingSet.contains(comparison)) {
						continue;
					}

					Instance currentInstance = getFeatures(NON_DUPLICATE, commonBlockIndices, comparison,0);
					int instanceLabel = (int) rf.classifyInstance(currentInstance);  
					double saida []=rf.distributionForInstance(currentInstance);
					if(saida[1]<1 && saida[1]>0.0){	                	
						if(flag<2){
							// System.out.println(saida[0] +  "  "+ saida[1] + " " + currentInstance.value(5));
							if(instanceLabel==1)
							{
								//currentInstance.setClassValue(value);
								data2.add(currentInstance);
								flag++;
								//data.remove(index)
								trainingSet.add(comparison);
							}
						}
					}
					// 		


					if (instanceLabel == DUPLICATE) {
						//count++;
						retainedEntities1.add(comparison.getEntityId1());
						retainedEntities2.add(comparison.getEntityId2());
					}

				}

			}

			int[] entityIds1 = Converter.convertCollectionToArray(retainedEntities1);
			int[] entityIds2 = Converter.convertCollectionToArray(retainedEntities2);
			int teste=0;
			for (int i = 0; i < entityIds1.length; i++) {
				//System.out.println(entityIds1[i] +" ---" + entityIds2[i]);
				teste++;
				Comparison comparison = new Comparison(dirtyER, entityIds1[i], entityIds2[i],0.0);
				if (areMatching(comparison)) {
					final IdDuplicates matchingPair = new IdDuplicates(entityIds1[i], entityIds2[i]);
					TdetectedDuplicates.add(matchingPair);  
					//System.out.println("match ->>>>" +entityIds1[i] +" ---" + entityIds2[i]);
				}	      
			}	 
			System.out.println(" data 2 --->"+data2.size());
			System.out.println("************Executed comparisons blocking\t:\t" 	);
			System.out.println("************Executed comparisons\t:\t" + entityIds1.length);
			System.out.println("************Detected duplicates\t:\t" + TdetectedDuplicates.size());
			System.out.println("************Detected duplicates\t:\t" + (double)TdetectedDuplicates.size()/8700 + "\n\n\n\n");

			retainedEntities1.clear();
			retainedEntities2.clear();
			TdetectedDuplicates.clear();  	        
		}


		//	        if(Math.abs((double)TdetectedDuplicates.size()/8700 -memoria)<0.01 && data2.size()<10){
		//	        	data2.remove(linha_arff-1);
		//	        	linha_arff--;
		//	        	//System.out.println("removeuu...." + memoria );       	   	
		//	        	
		//	        	
		//	        }else if(((double)TdetectedDuplicates.size()/8700 -memoria)<0.01 && data2.size()>10){
		//	        	data2.remove(linha_arff-1);
		//	        	linha_arff--;
		//	        	//System.out.println("removeuu...." + memoria );        		
		//        	}  	        
		//	        else{        	
		//	        	if((memoriaPares -entityIds1.length)<-11000 && data2.size()>15){
		//	        		data2.remove(linha_arff-1);
		//		        	linha_arff--;
		//		        	System.out.println("removeuu pares...." + memoriaPares  + "   "+ entityIds1.length); 
		//	        	}else{
		//	        		
		//	        	  memoria=(double)TdetectedDuplicates.size()/8700;
		//	        	  memoriaPares =entityIds1.length;
		//	        	  System.out.println("                    nao removeuu...." + memoria  + " "+(double)TdetectedDuplicates.size()/8700);
		//	        		System.err.println("data2.size()--->" +data2.size());
		//	        	  System.out.println("************Executed comparisons blocking\t:\t" 	);
		//	  	        System.out.println("************Executed comparisons\t:\t" + entityIds1.length);
		//	  	        System.out.println("************Detected duplicates\t:\t" + TdetectedDuplicates.size());
		//	  	        System.out.println("************Detected duplicates\t:\t" + (double)TdetectedDuplicates.size()/8700 + "\n\n\n\n");
		//	        	}
		//	        }



		retainedEntities1.clear();
		retainedEntities2.clear();
		TdetectedDuplicates.clear();

	}

	//	int[] primeiroBlock=new int[10];

	private void loadFileTrainingSet(HashMap<Integer, LinkedList<String>> hash) throws NumberFormatException, IOException {
		StringBuilder sb = new StringBuilder();
		String line = null;
		String[] splitLine;
		//Statement st = con.createStatement();
		String idA,idB = null, block = "";
		int countP=0,countN=0;
		trainingInstances.clear();
		sampleMatches.clear();
		sampleNonMatches.clear();

		BufferedReader alac_result = new BufferedReader(new FileReader("/tmp/levels_arff.txt"));				
		Random r=new Random();
		LinkedList<String> list =new LinkedList<String>();
		//int i=0;
		while((line=alac_result.readLine()) != null){
			list.add(line);
		}

		int flag=0;
		Collection<LinkedList<String>> colection = hash.values();
		Iterator<LinkedList<String>> iterator = colection.iterator();
		//for (int i = 0; i < hash.size(); i++) {
		while(iterator.hasNext()){
			LinkedList<String> l=iterator.next();
			//int rand=r.nextInt(l.size());
			//System.err.println("----" +l.size());
			for (int i = 0; i < l.size(); i++) {		
				String Nline=l.get(i).split(" ")[0];
				line = list.get(Integer.parseInt(Nline));
				//System.out.println(Nline+ " " +  +line);
				splitLine=line.split(",");
				idA=splitLine[0].split(":")[2];
				idB=splitLine[2].split(":")[2];
				block=splitLine[4].trim();
				String label=splitLine[3].trim();
				System.out.println(Nline+ "---- " + label + "   " +  line);
				Comparison comparison = new Comparison(true, Integer.parseInt(idA), Integer.parseInt(idB),0.0);
				trainingSet.add(comparison);
				List<Integer> commonBlockIndices = entityIndex.getCommonBlockIndices(Integer.parseInt(block), comparison);	
				if(commonBlockIndices!=null){
					Instance newInstance = getFeatures(label.equals("true")?1:0, commonBlockIndices, comparison,0.0);
					trainingInstances.add(newInstance);
					//				System.out.println();
					//				for (int i = 0; i < newInstance.numAttributes(); i++) {
					//					System.err.print(newInstance.value(i)+" ,");
					//				}
					//				System.err.println();					
					if(label.toLowerCase().contains("true"))
						countP++;
					else
						countN++;		
				}
			}
			//int i;
			//if(label.equals("false") && flag++<3)
			//	i--;		
		}
		System.out.println("valores  --> Positio -> " +countP  +"  negativos -> "+countN);
		sampleMatches.add((double) countP);///positivos
		sampleNonMatches.add((double) (countN)); //negativos		
	}

	private void descarta_allac() throws FileNotFoundException, IOException{

		BufferedReader alac_result = new BufferedReader(new FileReader("/tmp/levels_arff2.txt"));	
		PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("/tmp/alac.txt")), true);
		String line, line_alac; 
		BufferedReader br = new BufferedReader(new FileReader("/tmp/final_treina.txt"));
		//	br.readLine();//pula primeira linha
		//BufferedReader br = new BufferedReader(new FileReader("/tmp/levels_arff2.txt"));

		int flag=0;
		while((line=alac_result.readLine()) != null){

			while ((line_alac=br.readLine()) != null) {
				//System.out.println(line_alac);
				if(line.equals(line_alac)){
					flag=1;
					System.out.println( "hit   " + line_alac);				
					break;

				}
			}

			br= new BufferedReader(new FileReader("/tmp/final_treina.txt"));
			if(flag==0){
				out.println(line);
				flag=0;
			}

		}
		br = new BufferedReader(new FileReader("/tmp/final_treina.txt"));
		alac_result = new BufferedReader(new FileReader("/tmp/levels_arff2.txt"));	
		while((line=alac_result.readLine()) != null){

			while ((line_alac=br.readLine()) != null) {
				//System.out.println(line_alac);
				if(line.equals(line_alac)){
					flag=1;
					System.out.println( "hit   " + line_alac);				
					break;

				}
			}
			br= new BufferedReader(new FileReader("/tmp/final_treina.txt"));
			out.println(line);
		}

	}



	private int[] conta_niveis_hash(List<AbstractBlock> blocks, ExecuteBlockComparisons ebc) {

		int[] blockSize=  new int[100];
		for (int i = 0; i < 100; i++) {
			blockSize[i]=0;
			//	primeiroBlock[i]=0;
		}
		double sim=0.0;
		for ( AbstractBlock b:blocks) {






					final List<Integer> commonBlockIndices = entityIndex.getCommonBlockIndices(b.getBlockIndex(), c);
					if (commonBlockIndices == null) {
						continue;
					}
					//double sim = ProfileComparison.getJaccardSimilarity(ebc.exportEntityA(c.getEntityId1()), ebc.exportEntityB(c.getEntityId2()));
					
//					Double sim=ebc.getSImilarityAttribute(c.getEntityId1(),c.getEntityId2(),names);
//					if(sim>=1.0)
//						sim=0.99;
					
					double ibf1 = Math.log(noOfBlocks/entityIndex.getNoOfEntityBlocks(c.getEntityId1(), 0));
					double ibf2 = Math.log(noOfBlocks/entityIndex.getNoOfEntityBlocks(c.getEntityId2(), 1));
					try{
						sim = commonBlockIndices.size();//*ibf1*ibf2;	
					}catch (Exception e ){
						System.out.println(e.getMessage());
					}
					if(sim>20)
						blockSize[((int)Math.floor(20))]++;
					else
						blockSize[((int)Math.floor(sim))]++;

				}

			}

		}


		//
		for (int i = 0; i < 100; i++) {
			if(blockSize[i] !=0)
			//	perc[i]=(((double)tamanho)/(blockSize[i]));
			System.out.println(i + " tamanho do bloco "+  "  " + blockSize[i] );
			//totalPares += blockHash.blockSize[i];
		}
		return blockSize;

		//		int levels[] = new int[10];
		//		double perc[] = new double[10];
		//		for (int i = 0; i < levels.length; i++) {
		//			levels[i]=0;
		//		}
		//		for (AbstractBlock abstractBlock : blocks) {
		//			ComparisonIterator iterator = abstractBlock.getComparisonIterator();
		//			Comparison comparison;
		//			while(iterator.hasNext()){			
		//					 comparison = iterator.next(); 
		//					 comparison.sim=ebc.getSImilarityAttribute(comparison.getEntityId1(),comparison.getEntityId2(),names);		
		//					// System.out.println(((int)Math.floor(comparison.sim*10)));
		//					 levels[((int)Math.ceil(comparison.sim*10))]++;
		//			}			
		//		}

	}


	private double[] conta_niveis(List<AbstractBlock> blocks, ExecuteBlockComparisons ebc, int tamanho) {

		int levels[] = new int[10];
		double perc[] = new double[10];
		for (int i = 0; i < levels.length; i++) {
			levels[i]=0;
		}
		for (AbstractBlock abstractBlock : blocks) {
			ComparisonIterator iterator = abstractBlock.getComparisonIterator();
			Comparison comparison;
			while(iterator.hasNext()){			
				comparison = iterator.next(); 
				comparison.sim=ebc.getSImilarityAttribute(comparison.getEntityId1(),comparison.getEntityId2(),names);		
				// System.out.println(((int)Math.floor(comparison.sim*10)));
				levels[((int)Math.floor(comparison.sim*10))]++;
			}			
		}
		for (int i = 0; i < levels.length; i++) {
			perc[i]=((double)tamanho)/(levels[i]);
			System.out.println(i + " "+ perc[i] + "  " + levels[i] );
		}
		return perc;
	}
>>>>>>> teste

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

	protected void getTrainingSet(int iteration, ExecuteBlockComparisons ebc, int tamanho) {
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
				//System.out.println(block.getBlockIndex());

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
				Instance newInstance = getFeatures(match, commonBlockIndices, comparison,0.0);
				trainingInstances.add(newInstance);
			}
		}
		sampleMatches.add((double) trueMetadata);
		sampleNonMatches.add((double) (trainingSet.size() - trueMetadata));
	}
	int testando=0;

	private void callAllac(int i, int r) throws IOException {
		////Common common=new Common();
		//common.setSortFile("/");
		//common.setElementos("3,4,5,6");
		String line;
		String cmd;
		String userHome = System.getProperty("user.home");
		//String file ="/tmp/levels_arff_level"+i+"" + " /tmp/teste";
		String file ="/tmp/levels_arff" +set + " /tmp/teste";

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

	private int original(Random random, Comparison comparison, int trueMetadata, List<Integer> commonBlockIndices, int trueMetadata1, double nonMatchRatio){
		temp++;
		int match = NON_DUPLICATE; // false
		if (areMatching(comparison)) {
			if (random.nextDouble() < SAMPLE_SIZE) {
				trueMetadata1++;
				match = DUPLICATE; // true
			} else {
				return trueMetadata1;
			}
		} else if (nonMatchRatio <= random.nextDouble()) {
			return trueMetadata1;
		}

		trainingSet.add(comparison);
		Instance newInstance = getFeatures(match, commonBlockIndices, comparison,0.0);
		trainingInstances.add(newInstance);
		return trueMetadata1;
	}

	private void loadFileTrainingSet(Instances trainingInstances2) throws IOException, SQLException {

		StringBuilder sb = new StringBuilder();
		String line = null, splitLine[];
		//Statement st = con.createStatement();
		String idA,idB = null, block = "";
		int countP=0,countN=0;
		trainingInstances.clear();
		sampleMatches.clear();
		sampleNonMatches.clear();

		//for (int i = 0; i < 2; i++) 
		//{

		BufferedReader alac_result = new BufferedReader(new FileReader("/tmp/final_treina.txt"));

		//	BufferedReader alac_result = new BufferedReader(new FileReader("/tmp/levels_arff.txt"));	


		//BufferedReader br = new BufferedReader(new FileReader("/tmp/final_treina.txt"));
		//br.readLine();//pula primeira linha
		//BufferedReader br = new BufferedReader(new FileReader("/tmp/levels_arff2.txt"));
		String line_alac;
		int flag=0;
		Random r=new Random();
		int count=0;
		while((line=alac_result.readLine()) != null){

			//			while ((line_alac=br.readLine()) != null) {
			//				//System.out.println(line_alac);
			//				if(line.equals(line_alac)){
			//					flag=1;
			//					System.out.println( "hit   " + line_alac);				
			//					break;
			//					
			//				}
			//			}
			//	br= new BufferedReader(new FileReader("/tmp/final_treina.txt"));
			//	br.readLine();//pula primeira linha
			if(flag==0){				

				splitLine=line.split(",");
				idA=splitLine[0].split(":")[2];
				idB=splitLine[2].split(":")[2];
				block=splitLine[4].trim();
				String label=splitLine[3].trim();
				Comparison comparison = new Comparison(true, Integer.parseInt(idA), Integer.parseInt(idB),0.0);
				trainingSet.add(comparison);
				List<Integer> commonBlockIndices = entityIndex.getCommonBlockIndices(Integer.parseInt(block), comparison);

				for (int i = 0; i < commonBlockIndices.size(); i++) {
					System.out.print("---" + commonBlockIndices.get(i) + "\n ");
				}
				if(commonBlockIndices!=null){


					//					if(label.equals("false") ){
					//						if(((double)r.nextFloat())<0.8)
					//							continue;
					//					}
					//						
					//					if(label.equals("true") ){
					//						
					//						if(((double)r.nextFloat())<0.8)
					//							continue;
					//					}
					//Instance i =new Instance
					Instance newInstance = getFeatures(label.equals("true")?1:0, commonBlockIndices, comparison,0.0);
					//					if(countN==1){
					//						newInstance.setValue(0, 10);
					//						countN++;
					//					}
					Random ran =new Random();
					//					newInstance.setValue(3, 0);
					//					newInstance.setValue(4, 0);
					//					if(count>=0){
					//						if(label.toLowerCase().contains("true")){
					//							newInstance.setValue(0, 100);
					//							newInstance.setValue(1, 0.05);
					//						}else{							
					//							newInstance.setValue(0, 50);
					//							newInstance.setValue(1, 0.020);
					//							//newInstance.setValue(1, 1- ran.nextDouble()/10-0.5);
					//						}
					//						
					//						//newInstance.setValue(2, 0);
					//						
					//					}
					trainingInstances.add(newInstance);
					for (int i = 0; i < newInstance.numAttributes(); i++) {
						System.out.print(newInstance.value(i)+" ,");
					}
					//System.out.println( commonBlockIndices.size() + "  "+blocks.get(Integer.parseInt(block)).getNoOfComparisons());

					if(label.toLowerCase().contains("true"))
						countP++;
					else
						countN++;		
				}
			}
			flag=0;
			count++;
		}



		//		while ((line=br.readLine()) != null) {
		//			//System.out.println("loading "+ line);
		//			splitLine=line.split(",");
		//			idA=splitLine[0].split(":")[2];
		//			idB=splitLine[2].split(":")[2];
		//			block=splitLine[4].trim();
		//			String label=splitLine[3].trim();
		//
		//			//System.err.println("select * from base_scholar_clear where recA like '" + splitLine[0]+ "' and recB like  '" +splitLine[2]+"';");
		//			//    		ResultSet rs = st.executeQuery("select * from base_scholar_clear where recA like '%" + idA+ "%' and idB ="+idB);
		//			//			while( rs.next()){
		//			//				block=rs.getString("idA");
		//			//				label=rs.getString("label");
		//			//				if(label.equals("true"))
		//			//					countP++;
		//			//				else
		//
		//			//					countN++;
		//			//			}
		//			//	rs.close();
		////						if(i==0 && label.equals("false") && countN < 50000)
		////						{
		////							
		////							Comparison comparison= new Comparison(true, Integer.parseInt(idA), Integer.parseInt(idB),0.0);
		////							trainingSet.add(comparison);
		////							List<Integer> commonBlockIndices = entityIndex.getCommonBlockIndices(Integer.parseInt(block), comparison);
		////								
		////							if(commonBlockIndices!=null){	
		////								Instance newInstance = getFeatures(label.equals("true")?1:0, commonBlockIndices, comparison,0.0);
		////								trainingInstances.add(newInstance);								
		////								if(label.toLowerCase().contains("true"))
		////									countP++;
		////								else
		////									countN++;		
		////							}
		////						}
		////						System.out.println("valores  --> Positio -> " +countP  +"  negativos -> "+countN);
		//		//		if(i==1 && label.equals("true"))
		//				{
		//			//System.err.println(idA +" " +idB + " " + block);	
		//			Comparison comparison = new Comparison(true, Integer.parseInt(idA), Integer.parseInt(idB),0.0);
		//			trainingSet.add(comparison);
		//			List<Integer> commonBlockIndices = entityIndex.getCommonBlockIndices(Integer.parseInt(block), comparison);
		//
		//			if(commonBlockIndices!=null){	
		//				Instance newInstance = getFeatures(label.equals("true")?1:0, commonBlockIndices, comparison,0.0);
		//				trainingInstances.add(newInstance);
		////				for (int i = 0; i < newInstance.numAttributes(); i++) {
		////					System.err.print(newInstance.value(i)+" ,");
		////				}
		////				System.err.println();
		//				
		//				if(label.toLowerCase().contains("true"))
		//					countP++;
		//				else
		//					countN++;		
		//			}
		//		}
		//		//			System.out.println("valores  --> Positio -> " +countP  +"  negativos -> "+countN);
		//			}
		//			}
		System.out.println("valores  --> Positio -> " +countP  +"  negativos -> "+countN);
		sampleMatches.add((double) countP);///positivos
		sampleNonMatches.add((double) (countN)); //negativos

	}

	//int[] size=new int[10];
	int last_block=0;
	int lixo=0;
	Random random=new Random();
	private int getLevels(Comparison comparison, ExecuteBlockComparisons ebc, int i, PrintStream pstxt, PrintStream psarff, PrintStream[] pstxt_level, PrintStream[] psarff_level, double nonMatchRatio, double tamanho, int controle, double d) throws FileNotFoundException {
		String concatStringA;
		String concatStringB;
		int flag=1;

		Double sim=comparison.sim;


		{

			//			if(last_block!=comparison.teste ){
			//				for (int j = 0; j < 10; j++) {
			//					size[j]=0;
			//				}
			//				last_block=comparison.teste;		
			//			}
			////		//	System.out.println("XXXX -" + last_block+ " "+ comparison.teste + " "+(size[controle]) + " " + i);
			////			
			////			if(elements[controle]==tamanho)
			////				return 0;
			//			if( last_block==comparison.teste && size[controle]++>0){		
			//				//System.out.println("*****valor " + comparison.getEntityId1() + " ---  "+ comparison.getEntityId2() + " ---- " + sim);	
			//				if((controle-1==0) && size[controle-1]>0){
			//					return -1;
			//				}else if(controle==0 && size[controle+1]>0){
			//					return -1;
			//				}else{
			//					if(controle>0)
			//						return -1;
			//				}
			//				return -1;
			//			}




			//	if(elements[controle]<tamanho)
			{
				//					if(controle <0.5 && random.nextDouble()>0.30)
				//						return -1;
				//					else if(random.nextDouble()>0.7)
				//						return -1;
				//System.out.println("elements  -->" + elements[controle]);
				//					balance.put(comparison.getEntityId1(),comparison.getEntityId2());
				//
				Set<DataStructures.Attribute> setAtributtes = ebc.exportEntityA(comparison.getEntityId1());
				String sA[]=Converter.createVector(setAtributtes,comparison.getEntityId1());
				concatStringA=sA[0]+"::";////title,

				if(sA.length==0)
					return 1;
				for (int j = 0; j < sA.length; j++) {
					try{
						//System.err.print(sA[j]+ "  ");
						if(!sA[j].isEmpty())
							sA[j]=sA[j].replace(",", " ").replace(":", " ").replace("\n","");
						concatStringA=concatStringA.concat(sA[j]+":");				

					}catch(Exception e){
						concatStringA=concatStringA.concat(":  :");	
					}
				}
				setAtributtes = ebc.exportEntityB(comparison.getEntityId2());
				String sB[]=Converter.createVector(setAtributtes,comparison.getEntityId2());
				//    System.out.print( "  ---- ");
				concatStringB=sB[0]+"::";
				for (int j = 0; j < sB.length; j++) {
					try{
						//System.err.print(sB[j]+ "  ");
						if(!sB[j].isEmpty())
							sB[j]=sB[j].replace(",", " ").replace(":", " ").replace("\n","");
						concatStringB=concatStringB.concat(sB[j]+":");
					}catch (Exception e ){
						concatStringB=concatStringB.concat(": :");
					}
				}
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
				
				
//				if(newInstanceTemp.value(0)<80){
//					if(label.equals("false"))
//					if(random.nextInt(300000)>500)
//						return 0;				
//					
//				}else
//					if(random.nextInt(10000)>80)
//						return 0;
				
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
				psarff.println(label.contains("true")?1:0);
				//psarff_level[controle].println(label.contains("true")?1:0);
				///////////    	
				//FileUtilities.save_data_db( String.valueOf(i), sB[0],concatStringA,concatStringB, sim,label,null,pstxt,pstxt_level, psarff_level,k  );
				pstxt.println(concatStringA +","+sim+ ", " +concatStringB+ ","+label+ " ,"+String.valueOf(i));
//				pstxt_level[controle].println(concatStringA +","+sim+ ", " +concatStringB+ ","+label+ " ,"+String.valueOf(i));
//				pstxt_level[controle].flush();
				pstxt.flush();
				//	if(ele)
			//	elements[controle]++;
				//System.out.println("controle " + controle + " --> element "+elements[controle]);
				return -1;
			}
			//else{
			//System.out.println(controle + "tamanho --->> " + elements[controle]);
			//	lixo=0;

		}
		//return 0;
	}

	//	return -1;
	//}
	
	
	   public static double getJaccardSimilarity(int[] tokens1, int[] tokens2) {
	        double commonTokens = 0.0;
	        int noOfTokens1 = tokens1.length;
	        int noOfTokens2 = tokens2.length;
	        for (int i = 0; i < noOfTokens1; i++) {
	            for (int j = 0; j < noOfTokens2; j++) {
	                if (tokens2[j] < tokens1[i]) {
	                    continue;
	                }

	                if (tokens1[i] < tokens2[j]) {
	                    break;
	                }

	                if (tokens1[i] == tokens2[j]) {
	                    commonTokens++;
	                }
	            }
	        }
	        return commonTokens / (noOfTokens1 + noOfTokens2 - commonTokens);
	    }


	private void prepareStatistics() {
		sampleMatches = new ArrayList<Double>();
		sampleNonMatches = new ArrayList<Double>();
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

	//	public void printStatisticsB(BufferedWriter writer)throws IOException {
	//		
	//		for (int i = 0; i < overheadTimes.length; i++) {
	//			Double d = (sampleDuplicates[0].get(i))/(duplicates.size())*100.0;
	//			writer.write("dup  " + sampleMatches.get(i).toString() + " nondup "+ sampleNonMatches.get(i).toString() +" sampleComparisons " + sampleComparisons[0].get(i).toString() + " pc " + d.toString() );
	//		}
	//		
	//	}

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