/*
 * Title:        EdgeCloudSim - Simulation Logger
 * 
 * Description: 
 * SimLogger is responsible for storing simulation events/results
 * in to the files in a specific format.
 * Format is decided in a way to use results in matlab efficiently.
 * If you need more results or another file format, you should modify
 * this class.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.utils;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.core.CloudSim;

import com.google.common.collect.ArrayListMultimap;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import jdk.internal.org.objectweb.asm.tree.analysis.Value;

public class SimLogger {
	public static enum TASK_STATUS {
		CREATED, UPLOADING, PROCESSING, DOWNLOADING, COMLETED, REJECTED_DUE_TO_VM_CAPACITY, REJECTED_DUE_TO_BANDWIDTH, UNFINISHED_DUE_TO_BANDWIDTH, UNFINISHED_DUE_TO_MOBILITY
	}

	private static CloudletSchedulerSpaceShared cloudletScheduler;
	private static boolean fileLogEnabled;
	private static boolean printLogEnabled;
	private String filePrefix;
	private String outputFolder;
	private Map<Integer, LogItem> taskMap;
	private LinkedList<VmLoadLogItem> vmLoadList;
	private ArrayListMultimap<String, Double> completionTimes = ArrayListMultimap.create();
	
	private ArrayListMultimap<String, Double> executionTimes = ArrayListMultimap.create();
	
	private ArrayListMultimap<String, Double> transferTimes = ArrayListMultimap.create();
	private HashMap<String, NormDistr> distributions = new HashMap<>();
	public ETCMatrix matrix;
	
	private HashMap<String, NormDistr> ettDistributions = new HashMap<>();
	
	public ETTMatrix ettMatrix;
	public PTCMatrix ptcMatrix;
	private HashMap<String, NormDistr> ptcDistributions = new HashMap<>();
	
	public static double failTaskPercent;
	public static double toatTasks;
	public static double result;
	public static int numOfTasks;
	public static double processingTime;
	public static double netDelay;
	public static double onlyLanDelay;
	public static double vMu;
	
	private static DecimalFormat df2 = new DecimalFormat("#.##");
	
	
	public static double getLanDelay() {
		return onlyLanDelay;
	}

	public static void setLanDelay(double onlyLanDelay) {
		SimLogger.onlyLanDelay = onlyLanDelay;
	}

	public static double getNetDelay() {
		return netDelay;
	}

	public static void setNetDelay(double netDelay) {
		SimLogger.netDelay = netDelay;
	}

	public static double getProcessingTime() {
		return processingTime;
	}

	public static void setProcessingTime(double processingTime) {
		SimLogger.processingTime = processingTime;
	}

	public static double onlyEdgefailedTask;
	
	
	public static double getOnlyEdgefailedTask() {
		return onlyEdgefailedTask;
	}

	public static void setOnlyEdgefailedTask(double onlyEdgefailedTask) {
		SimLogger.onlyEdgefailedTask = onlyEdgefailedTask;
	}

	public double getToatTasks() {
		return toatTasks;
	}

	public void setToatTasks(double toatTasks) {
		SimLogger.toatTasks = toatTasks;
	}

	public void setFailTaskPercent(double a) {
		
		this.failTaskPercent = a;
	}
	
	public double getFailTaskPercent() {
		return failTaskPercent;
	}
	
	private int dlMisCounter = 0 ;
	
	public int getDlMisCounter() {
		return dlMisCounter;
	}

	public void setDlMisCounter(int dlMisCounter) {
		this.dlMisCounter = dlMisCounter;
	}
	
	
	
	double failedTask = 0;

	public double getFailedTask() {
		return failedTask;
	}

	public void setFailedTask(double failedTask) {
		this.failedTask = failedTask;
	}

	private static SimLogger singleton = new SimLogger();

	/*
	 * A private Constructor prevents any other class from instantiating.
	 */
	private SimLogger() {
		fileLogEnabled = false;
		printLogEnabled = false;
	}

	/* Static 'instance' method */
	public static SimLogger getInstance() {
		return singleton;
	}

	public static void enableFileLog() {
		fileLogEnabled = true;
	}

	public static void enablePrintLog() {
		printLogEnabled = true;
	}

	public static boolean isFileLogEnabled() {
		return fileLogEnabled;
	}

	public static void disablePrintLog() {
		printLogEnabled = false;
	}

	private void appendToFile(BufferedWriter bw, String line) throws IOException {
		bw.write(line);
		bw.newLine();
	}

	public static void printLine(String msg) {
		if (printLogEnabled)
			System.out.println(msg);
	}

	public static void print(String msg) {
		if (printLogEnabled)
			System.out.print(msg);
	}

	public void simStarted(String outFolder, String fileName) {
		filePrefix = fileName;
		outputFolder = outFolder;
		taskMap = new HashMap<Integer, LogItem>();
		vmLoadList = new LinkedList<VmLoadLogItem>();
	}

	public void addLog(double taskStartTime, int taskId, int taskType, int taskLenght, int taskInputType,
			int taskOutputSize, int hostID, int vmId) {
		//printLine(vmId+"->"+vmId);
		taskMap.put(taskId, new LogItem(taskStartTime, taskType, taskLenght, taskInputType, taskOutputSize, hostID, vmId));
	}

	public void uploadStarted(int taskId, double taskUploadTime) {
		taskMap.get(taskId).taskUploadStarted(taskUploadTime);
	}

	public void uploaded(int taskId, int datacenterId, int hostId, int vmId, int vmType,double time) {
		taskMap.get(taskId).taskUploaded(datacenterId, hostId, vmId, vmType,time);
	}

	public void downloadStarted(int taskId, double taskDownloadTime) {
		taskMap.get(taskId).taskDownloadStarted(taskDownloadTime);
	}

	public void downloaded(int taskId, double taskEndTime) {
		taskMap.get(taskId).taskDownloaded(taskEndTime);
	}

	public void rejectedDueToVMCapacity(int taskId, double taskRejectTime, int host) {
		//System.out.print("..."+ taskRejectTime + "..."+ taskId+ "..."+ host+ "\n");
		
		taskMap.get(taskId).taskRejectedDueToVMCapacity(taskRejectTime);
	}

	public void rejectedDueToBandwidth(int taskId, double taskRejectTime, int vmType) {
		taskMap.get(taskId).taskRejectedDueToBandwidth(taskRejectTime, vmType);
	}

	public void failedDueToBandwidth(int taskId, double taskRejectTime) {
		taskMap.get(taskId).taskFailedDueToBandwidth(taskRejectTime);
	}

	public void failedDueToMobility(int taskId, double time) {
		taskMap.get(taskId).taskFailedDueToMobility(time);
	}

	public void addVmUtilizationLog(double time, double load) {
		vmLoadList.add(new VmLoadLogItem(time, load));
	}
	
	public void initPTC(int edgeNo) throws IOException {
		
		int numOfAppTypes = SimSettings.getInstance().getTaskLookUpTable().length;
		int numOfDataCenters = SimSettings.getInstance().getNumOfEdgeHosts();
		int numOfVMs = SimSettings.getInstance().getNumOfEdgeVMs();
		double cpuTime = 0;
		int taskType=0;
		int hostNo =0;
		
		for (Map.Entry<Integer, LogItem> entry : taskMap.entrySet()) {
			LogItem value = entry.getValue();
			taskType = value.getTaskType();
			hostNo = value.getHostID();
			if(value.getStatus() == SimLogger.TASK_STATUS.COMLETED) {
			    String key = value.getHostID() + "." +value.getVmId()+"."+ value.getTaskType();
				
				cpuTime = value.getEndTime()-value.getProcStartTime();

		    }
				
		}
		
		FileWriter fw= new FileWriter("/home/c00303945/ResearchWork/Fall2019/EdgePTC/Edge"+edgeNo+"/edgePTC"+edgeNo+".txt", true);
		PrintWriter printWriter = new PrintWriter(fw);
		
		for(int row=1;row<=numOfAppTypes;row++) {
			
			for(int col=1;col<=numOfDataCenters;col++) {
								
					printWriter.print(0+",");
				
			}
			printWriter.print('\n');
		}
		printWriter.close();
		
	}
	
	
	public void distributionCalculation(int hostID, double mu, LogItem val) throws IOException {
		
		double[] arrList = new double[26000];
		
		int numOfAppTypes = SimSettings.getInstance().getTaskLookUpTable().length;
		int numOfDataCenters = SimSettings.getInstance().getNumOfEdgeHosts();
		int numOfVMs = SimSettings.getInstance().getNumOfEdgeVMs();
		double cpuTime = 0;
		int taskType = 0;
		int hostNo =0;
		
		for (Map.Entry<Integer, LogItem> entry : taskMap.entrySet()) {
			LogItem value = entry.getValue();
			taskType = value.getTaskType();
			hostNo = value.getHostID();
			if(value.getStatus() == SimLogger.TASK_STATUS.COMLETED) {
			    String key = value.getHostID() + "." +value.getVmId()+"."+ value.getTaskType();
				cpuTime = value.getEndTime()-value.getProcStartTime();
		    }
		}
				
        //vMu = vMu+cpuTime;
        
        for(int i=1;i<9;i++) {
				
				if(i==hostID) {
					
					File file = new File("/home/c00303945/ResearchWork/Fall2019/EdgePTC/Edge"+hostID+"/edgePTC"+hostID+".txt");
					BufferedReader br = new BufferedReader(new FileReader(file));
					
					int index = 1;
			        String line = null;
			        			        
			        while ((line=br.readLine())!=null) {
			            
			        	if(index == taskType)
			        	{
			        		String[] lineArray = line.split(",");
			        		arrList[index]= Double.parseDouble(lineArray[hostID]);//extracting arrival rate from file
			        	}
			        	else {
			        		index++;
			        	}
			         } 
					
					
					FileWriter fileWrt= new FileWriter("/home/c00303945/ResearchWork/Fall2019/EdgePTC/Edge"+hostID+"/edgePTC"+hostID+".txt", true);
					PrintWriter printWriter = new PrintWriter(fileWrt);

					for(int row=1;row<=numOfAppTypes;row++) {
						
						for(int col=1;col<=numOfDataCenters;col++) {
											
							if(row==val.getTaskType() && col==hostID) {
								printWriter.print(vMu+" ");
								break;
							}
						}
						
					}
					printWriter.close();
				
					break;
				}
				
				
			}
		
		
	}
	
	
	public void simPaused() throws IOException {
		int numOfAppTypes = SimSettings.getInstance().getTaskLookUpTable().length;
		int numOfDataCenters = SimSettings.getInstance().getNumOfEdgeHosts();
		int numOfVMs = SimSettings.getInstance().getNumOfEdgeVMs();
		
		for(int i=1;i<=numOfDataCenters;i++) {
			initPTC(i);
		}
		
		
		
		createETCDistribution();
		createEttDistribution();
		createPTCDistribution();
		
		double value1 = SimSettings.getInstance().getTaskLookUpTable()[0][5];
		
		matrix = new ETCMatrix(numOfDataCenters, numOfAppTypes, distributions,numOfTasks);
		
		ettMatrix = new ETTMatrix(numOfDataCenters,numOfAppTypes, ettDistributions);
		
		ptcMatrix = new PTCMatrix(numOfDataCenters, numOfVMs, numOfAppTypes, ptcDistributions);
		
	}
	
	public void setInitialDC(int taskId, int DC) {
		taskMap.get(taskId).setInitDC(DC);
	}
	
	private void createETCDistribution() {
		numOfTasks = 0;
		for (Map.Entry<Integer, LogItem> entry : taskMap.entrySet()) {
			LogItem value = entry.getValue();
			if(value.getStatus() == SimLogger.TASK_STATUS.COMLETED) {
				    numOfTasks++;
					String key = value.getHostID() + "." + value.getTaskType();
					
					//double processingTime = value.getServiceTime()- value.getNetworkDelay();
					double processingTime = value.getServiceTime();
					completionTimes.put(key, processingTime);
			}

			
			Map <String, Collection<Double>> newMap = completionTimes.asMap();
			for(String taskTbaseST : newMap.keySet())	{
				
				List<Double> times = completionTimes.get(taskTbaseST);
				double sum = 0;
				double sqsum = 0;
				for(Double time: times) {
					sum += time;
				}
				double mu = sum/times.size();
				if (mu == sum) {
					 double sigma = 0.0;
					 NormDistr distr = new NormDistr(mu, sigma);
						distributions.put(taskTbaseST, distr);
				}
				
				else {
					for(Double time: times) {
						sqsum += Math.pow(time-mu, 2);
					}
					
					double sigma = Math.sqrt(sqsum/(times.size()-1));
					NormDistr distr = new NormDistr(mu, sigma);
					distributions.put(taskTbaseST, distr);
					}
				}
			}
		
	}
	
	private void createPTCDistribution() {
		
		for (Map.Entry<Integer, LogItem> entry : taskMap.entrySet()) {
			LogItem value = entry.getValue();
			
			//System.out.println(" Task Type "+ value.getTaskType() + " host id "+ value.getHostID());
					
			if(value.getStatus() == SimLogger.TASK_STATUS.COMLETED) {
				    String key = value.getHostID() + "." +value.getVmId()+"."+ value.getTaskType();
					
					double cpuTime = value.getEndTime()-value.getProcStartTime();
					executionTimes.put(key, cpuTime);
			}
			
			
			Map <String, Collection<Double>> newMap = executionTimes.asMap();
			for(String taskTbaseST : newMap.keySet())	{
				
				List<Double> times = executionTimes.get(taskTbaseST);
				double sum = 0;
				double sqsum = 0;
				for(Double time: times) {
					sum += time;
				}
				double mu = sum/times.size();
				
				if (mu == sum) {
					 double sigma = 0.0;
					 NormDistr distr = new NormDistr(mu, sigma);
					 
					 ptcDistributions.put(taskTbaseST, distr);
				}
				else {
					for(Double time: times) {
						sqsum += Math.pow(time-mu, 2);
					}
					
					double sigma = Math.sqrt(sqsum/(times.size()-1));
					NormDistr distr = new NormDistr(mu, sigma);
					ptcDistributions.put(taskTbaseST, distr);
					}
				}
			
		
			}
	}
	
		
	private void createEttDistribution() {
		NormalDistribution[][] nrmlRngList = new NormalDistribution[SimSettings.APP_TYPES.values().length][1];
		
		// getting upload data size from normal distribution
		for(int i=0; i<SimSettings.APP_TYPES.values().length; i++) {
			nrmlRngList[i][0] = new NormalDistribution(SimSettings.getInstance().getTaskLookUpTable()[i][5],10);
		}
		
		
		
		for (Map.Entry<Integer, LogItem> entry : taskMap.entrySet()) {
			LogItem value = entry.getValue();
			Random r = new Random();
			
			
			if(value.getStatus() == SimLogger.TASK_STATUS.COMLETED) {
					//String key = value.getHostID() + "." + value.getTaskType();
					String key = value.getInitialDC() + "." + value.getHostID();
					
					//double trasnferTime = value.getNetworkDelay();
					double transferTime = value.getProcStartTime() - value.getSubmissionTime() + value.getTransferTime();
					
					double mbitMean = (SimSettings.getInstance().getTaskLookUpTable()[value.getTaskType()][5]*8);
					
					double nwtrtime = Math.abs(r.nextGaussian()*(10+mbitMean)/r.nextGaussian()*(14.86+88.27));
					
					//System.out.println(" Task size in Mbits "+Math.abs(mbitMean));
					
					//System.out.println(" Max Channel capacity "+Math.abs(r.nextGaussian()*(14.86+88.27)));
					//System.out.println(" transfer time of task type "+value.getTaskType()+" is "+(nwtrtime/10000));
					
					transferTimes.put(key, nwtrtime);
				}
			Map <String, Collection<Double>> newMap = transferTimes.asMap();
			for(String taskTbaseST : newMap.keySet())	{
				
				List<Double> times = transferTimes.get(taskTbaseST);
				double sum = 0;
				double sqsum = 0;
				for(Double time: times) {
					sum += time;
				}
				double mu = sum/times.size();
				if (mu == sum) {
					 double sigma = 0.0;
					 NormDistr distr = new NormDistr(mu, sigma);
					 ettDistributions.put(taskTbaseST, distr);
				}
				
				else {
					for(Double time: times) {
						sqsum += Math.pow(time-mu, 2);
					}
					
					double sigma = Math.sqrt(sqsum/(times.size()-1));
					NormDistr distr = new NormDistr(mu, sigma);
					ettDistributions.put(taskTbaseST, distr);
					}
				}
			}
		
	}
	
	
	public void simStopped() throws IOException {
		int numOfAppTypes = SimSettings.getInstance().getTaskLookUpTable().length;

		File successFile = null, failFile = null, vmLoadFile = null, locationFile = null;
		FileWriter successFW = null, failFW = null, vmLoadFW = null, locationFW = null;
		BufferedWriter successBW = null, failBW = null, vmLoadBW = null, locationBW = null;

		// Save generic results to file for each app type. last index is average
		// of all app types
		File[] genericFiles = new File[numOfAppTypes + 1];
		FileWriter[] genericFWs = new FileWriter[numOfAppTypes + 1];
		BufferedWriter[] genericBWs = new BufferedWriter[numOfAppTypes + 1];

		// extract following values for each app type. last index is average of
		// all app types
		int[] uncompletedTask = new int[numOfAppTypes + 1];
		int[] uncompletedTaskOnCloud = new int[numOfAppTypes + 1];
		int[] uncompletedTaskOnCloudlet = new int[numOfAppTypes + 1];

		int[] completedTask = new int[numOfAppTypes + 1];
		int[] completedTaskOnCloud = new int[numOfAppTypes + 1];
		int[] completedTaskOnCloudlet = new int[numOfAppTypes + 1];

		int[] failedTask = new int[numOfAppTypes + 1];
		int[] failedTaskOnCloud = new int[numOfAppTypes + 1];
		int[] failedTaskOnCloudlet = new int[numOfAppTypes + 1];

		double[] networkDelay = new double[numOfAppTypes + 1];
		double[] wanDelay = new double[numOfAppTypes + 1];
		double[] lanDelay = new double[numOfAppTypes + 1];

		double[] serviceTime = new double[numOfAppTypes + 1];
		double[] serviceTimeOnCloud = new double[numOfAppTypes + 1];
		double[] serviceTimeOnCloudlet = new double[numOfAppTypes + 1];

		double[] processingTime = new double[numOfAppTypes + 1];
		double[] processingTimeOnCloud = new double[numOfAppTypes + 1];
		double[] processingTimeOnCloudlet = new double[numOfAppTypes + 1];

		double[] cost = new double[numOfAppTypes + 1];
		int[] failedTaskDuetoBw = new int[numOfAppTypes + 1];
		int[] failedTaskDuetoLanBw = new int[numOfAppTypes + 1];
		int[] failedTaskDuetoWanBw = new int[numOfAppTypes + 1];
		int[] failedTaskDuetoMobility = new int[numOfAppTypes + 1];
		int[] rejectedTaskDoToVmCapacity = new int[numOfAppTypes + 1];

		// open all files and prepare them for write
		if (fileLogEnabled) {
			if (SimSettings.getInstance().getDeepFileLoggingEnabled()) {
				successFile = new File(outputFolder, filePrefix + "_SUCCESS.log");
				successFW = new FileWriter(successFile, true);
				successBW = new BufferedWriter(successFW);

				failFile = new File(outputFolder, filePrefix + "_FAIL.log");
				failFW = new FileWriter(failFile, true);
				failBW = new BufferedWriter(failFW);
			}

			vmLoadFile = new File(outputFolder, filePrefix + "_VM_LOAD.log");
			vmLoadFW = new FileWriter(vmLoadFile, true);
			vmLoadBW = new BufferedWriter(vmLoadFW);

			locationFile = new File(outputFolder, filePrefix + "_LOCATION.log");
			locationFW = new FileWriter(locationFile, true);
			locationBW = new BufferedWriter(locationFW);

			for (int i = 0; i < numOfAppTypes + 1; i++) {
				String fileName = "ALL_APPS_GENERIC.log";

				if (i < numOfAppTypes) {
					// if related app is not used in this simulation, just
					// discard it
					if (SimSettings.getInstance().getTaskLookUpTable()[i][0] == 0)
						continue;

					fileName = SimSettings.APP_TYPES.values()[i] + "_GENERIC.log";
				}

				genericFiles[i] = new File(outputFolder, filePrefix + "_" + fileName);
				genericFWs[i] = new FileWriter(genericFiles[i], true);
				genericBWs[i] = new BufferedWriter(genericFWs[i]);
				appendToFile(genericBWs[i], "#auto generated file!");
			}

			if (SimSettings.getInstance().getDeepFileLoggingEnabled()) {
				appendToFile(successBW, "#auto generated file!");
				appendToFile(failBW, "#auto generated file!");
			}

			appendToFile(vmLoadBW, "#auto generated file!");
			appendToFile(locationBW, "#auto generated file!");
		}

		// extract the result of each task and write it to the file if required
		for (Map.Entry<Integer, LogItem> entry : taskMap.entrySet()) {
			Integer key = entry.getKey();
			LogItem value = entry.getValue();
			

			if (value.isInWarmUpPeriod())
				continue;

			if (value.getStatus() == SimLogger.TASK_STATUS.COMLETED) {
				completedTask[value.getTaskType()]++;

				if (value.getVmType() == SimSettings.VM_TYPES.CLOUD_VM.ordinal())
					completedTaskOnCloud[value.getTaskType()]++;
				else
					completedTaskOnCloudlet[value.getTaskType()]++;
			} else {
				failedTask[value.getTaskType()]++;

				//SimLogger.printLine(" Task type "+value.getTaskType()+" failed on host : "+value.getHostID());
				
				if (value.getVmType() == SimSettings.VM_TYPES.CLOUD_VM.ordinal())
					failedTaskOnCloud[value.getTaskType()]++;
				else
					failedTaskOnCloudlet[value.getTaskType()]++;
			}

			if (value.getStatus() == SimLogger.TASK_STATUS.COMLETED) {
				cost[value.getTaskType()] += value.getCost();
				serviceTime[value.getTaskType()] += value.getServiceTime();
				networkDelay[value.getTaskType()] += value.getNetworkDelay();
				processingTime[value.getTaskType()] += (value.getServiceTime() - value.getNetworkDelay());
				
				//double exTime = value.getEndTime() - value.getProcStartTime();
				//System.out.println(" Execution time of task in VM "+ value.getVmId() + " is "+ exTime+" host "+ value.getHostID());

				if (value.getVmType() == SimSettings.VM_TYPES.CLOUD_VM.ordinal()) {
					wanDelay[value.getTaskType()] += value.getNetworkDelay();
					serviceTimeOnCloud[value.getTaskType()] += value.getServiceTime();
					processingTimeOnCloud[value.getTaskType()] += (value.getServiceTime() - value.getNetworkDelay());
				} else {
					lanDelay[value.getTaskType()] += value.getNetworkDelay();
					serviceTimeOnCloudlet[value.getTaskType()] += value.getServiceTime();
					processingTimeOnCloudlet[value.getTaskType()] += (value.getServiceTime() - value.getNetworkDelay());
				}

				if (fileLogEnabled && SimSettings.getInstance().getDeepFileLoggingEnabled())
					appendToFile(successBW, value.toString(key));
			} else if (value.getStatus() == SimLogger.TASK_STATUS.REJECTED_DUE_TO_VM_CAPACITY) {
				rejectedTaskDoToVmCapacity[value.getTaskType()]++;
				if (value.getVmType() == SimSettings.VM_TYPES.CLOUD_VM.ordinal())
					if (fileLogEnabled && SimSettings.getInstance().getDeepFileLoggingEnabled())
						appendToFile(failBW, value.toString(key));
			} else if (value.getStatus() == SimLogger.TASK_STATUS.REJECTED_DUE_TO_BANDWIDTH
					|| value.getStatus() == SimLogger.TASK_STATUS.UNFINISHED_DUE_TO_BANDWIDTH) {
				failedTaskDuetoBw[value.getTaskType()]++;
				if (value.getVmType() == SimSettings.VM_TYPES.CLOUD_VM.ordinal())
					failedTaskDuetoWanBw[value.getTaskType()]++;
				else
					failedTaskDuetoLanBw[value.getTaskType()]++;

				if (fileLogEnabled && SimSettings.getInstance().getDeepFileLoggingEnabled())
					appendToFile(failBW, value.toString(key));
			} else if (value.getStatus() == SimLogger.TASK_STATUS.UNFINISHED_DUE_TO_MOBILITY) {
				failedTaskDuetoMobility[value.getTaskType()]++;
				if (fileLogEnabled && SimSettings.getInstance().getDeepFileLoggingEnabled())
					appendToFile(failBW, value.toString(key));
			} else {
				uncompletedTask[value.getTaskType()]++;
				if (value.getVmType() == SimSettings.VM_TYPES.CLOUD_VM.ordinal())
					uncompletedTaskOnCloud[value.getTaskType()]++;
				else {
					uncompletedTaskOnCloudlet[value.getTaskType()]++;
					String vmName="cloud";
					
					if(value.getVmType() != SimSettings.VM_TYPES.CLOUD_VM.ordinal()) {
						
						vmName = "Edge";
						value.getHostID();
						
					}
					
					//SimLogger.printLine("Uncomplted task of task type  :"+value.getTaskType()+" in host "+value.getHostID()+" vm type :"+vmName);
				
				}
			
			}
		}

		// calculate total values
		uncompletedTask[numOfAppTypes] = IntStream.of(uncompletedTask).sum();
		uncompletedTaskOnCloud[numOfAppTypes] = IntStream.of(uncompletedTaskOnCloud).sum();
		uncompletedTaskOnCloudlet[numOfAppTypes] = IntStream.of(uncompletedTaskOnCloudlet).sum();

		completedTask[numOfAppTypes] = IntStream.of(completedTask).sum();
		completedTaskOnCloud[numOfAppTypes] = IntStream.of(completedTaskOnCloud).sum();
		completedTaskOnCloudlet[numOfAppTypes] = IntStream.of(completedTaskOnCloudlet).sum();

		failedTask[numOfAppTypes] = IntStream.of(failedTask).sum();
		failedTaskOnCloud[numOfAppTypes] = IntStream.of(failedTaskOnCloud).sum();
		failedTaskOnCloudlet[numOfAppTypes] = IntStream.of(failedTaskOnCloudlet).sum();

		networkDelay[numOfAppTypes] = DoubleStream.of(networkDelay).sum();
		lanDelay[numOfAppTypes] = DoubleStream.of(lanDelay).sum();
		wanDelay[numOfAppTypes] = DoubleStream.of(wanDelay).sum();

		serviceTime[numOfAppTypes] = DoubleStream.of(serviceTime).sum();
		serviceTimeOnCloud[numOfAppTypes] = DoubleStream.of(serviceTimeOnCloud).sum();
		serviceTimeOnCloudlet[numOfAppTypes] = DoubleStream.of(serviceTimeOnCloudlet).sum();

		processingTime[numOfAppTypes] = DoubleStream.of(processingTime).sum();
		processingTimeOnCloud[numOfAppTypes] = DoubleStream.of(processingTimeOnCloud).sum();
		processingTimeOnCloudlet[numOfAppTypes] = DoubleStream.of(processingTimeOnCloudlet).sum();

		cost[numOfAppTypes] = DoubleStream.of(cost).sum();
		failedTaskDuetoBw[numOfAppTypes] = IntStream.of(failedTaskDuetoBw).sum();
		failedTaskDuetoWanBw[numOfAppTypes] = IntStream.of(failedTaskDuetoWanBw).sum();
		failedTaskDuetoLanBw[numOfAppTypes] = IntStream.of(failedTaskDuetoLanBw).sum();
		failedTaskDuetoMobility[numOfAppTypes] = IntStream.of(failedTaskDuetoMobility).sum();
		rejectedTaskDoToVmCapacity[numOfAppTypes] = IntStream.of(rejectedTaskDoToVmCapacity).sum();

		// calculate server load
		double totalVmLoad = 0;
		for (VmLoadLogItem entry : vmLoadList) {
			totalVmLoad += entry.getLoad();
			if (fileLogEnabled)
				appendToFile(vmLoadBW, entry.toString());
		}

		if (fileLogEnabled) {
			// write location info to file
			for (int t = 1; t < (SimSettings.getInstance().getSimulationTime()
					/ SimSettings.getInstance().getVmLocationLogInterval()); t++) {
				int[] locationInfo = new int[SimSettings.PLACE_TYPES.values().length];
				Double time = t * SimSettings.getInstance().getVmLocationLogInterval();

				if (time < SimSettings.getInstance().getWarmUpPeriod())
					continue;

				for (int i = 0; i < SimManager.getInstance().getNumOfMobileDevice(); i++) {

					Location loc = SimManager.getInstance().getMobilityModel().getLocation(i, time);
					SimSettings.PLACE_TYPES placeType = loc.getPlaceType();
					locationInfo[placeType.ordinal()]++;
				}

				locationBW.write(time.toString());
				for (int i = 0; i < locationInfo.length; i++)
					locationBW.write(SimSettings.DELIMITER + locationInfo[i]);

				locationBW.newLine();
			}

			for (int i = 0; i < numOfAppTypes + 1; i++) {

				if (i < numOfAppTypes) {
					// if related app is not used in this simulation, just
					// discard it
					if (SimSettings.getInstance().getTaskLookUpTable()[i][0] == 0)
						continue;
				}

				// check if the divisor is zero in order to avoid division by
				// zero problem
				double _serviceTime = (completedTask[i] == 0) ? 0.0 : (serviceTime[i] / (double) completedTask[i]);
				double _networkDelay = (completedTask[i] == 0) ? 0.0 : (networkDelay[i] / (double) completedTask[i]);
				double _processingTime = (completedTask[i] == 0) ? 0.0 : (processingTime[i] / (double) completedTask[i]);
				double _vmLoad = (vmLoadList.size() == 0) ? 0.0 : (totalVmLoad / (double) vmLoadList.size());
				double _cost = (completedTask[i] == 0) ? 0.0 : (cost[i] / (double) completedTask[i]);

				// write generic results
				String genericResult1 = Integer.toString(completedTask[i]) + SimSettings.DELIMITER
						+ Integer.toString(failedTask[i]) + SimSettings.DELIMITER 
						+ Integer.toString(uncompletedTask[i]) + SimSettings.DELIMITER 
						+ Integer.toString(failedTaskDuetoBw[i]) + SimSettings.DELIMITER
						+ Double.toString(_serviceTime) + SimSettings.DELIMITER 
						+ Double.toString(_processingTime) + SimSettings.DELIMITER 
						+ Double.toString(_networkDelay) + SimSettings.DELIMITER
						+ Double.toString(_vmLoad) + SimSettings.DELIMITER 
						+ Double.toString(_cost) + SimSettings.DELIMITER 
						+ Integer.toString(rejectedTaskDoToVmCapacity[i]) + SimSettings.DELIMITER 
						+ Integer.toString(failedTaskDuetoMobility[i]);

				// check if the divisor is zero in order to avoid division by
				// zero problem
				double _lanDelay = (completedTaskOnCloudlet[i] == 0) ? 0.0
						: (lanDelay[i] / (double) completedTaskOnCloudlet[i]);
				double _serviceTimeOnCloudlet = (completedTaskOnCloudlet[i] == 0) ? 0.0
						: (serviceTimeOnCloudlet[i] / (double) completedTaskOnCloudlet[i]);
				double _processingTimeOnCloudlet = (completedTaskOnCloudlet[i] == 0) ? 0.0
						: (processingTimeOnCloudlet[i] / (double) completedTaskOnCloudlet[i]);
				String genericResult2 = Integer.toString(completedTaskOnCloudlet[i]) + SimSettings.DELIMITER
						+ Integer.toString(failedTaskOnCloudlet[i]) + SimSettings.DELIMITER
						+ Integer.toString(uncompletedTaskOnCloudlet[i]) + SimSettings.DELIMITER
						+ Integer.toString(failedTaskDuetoLanBw[i]) + SimSettings.DELIMITER
						+ Double.toString(_serviceTimeOnCloudlet) + SimSettings.DELIMITER
						+ Double.toString(_processingTimeOnCloudlet) + SimSettings.DELIMITER
						+ Double.toString(_lanDelay);

				// check if the divisor is zero in order to avoid division by
				// zero problem
				double _wanDelay = (completedTaskOnCloud[i] == 0) ? 0.0
						: (wanDelay[i] / (double) completedTaskOnCloud[i]);
				double _serviceTimeOnCloud = (completedTaskOnCloud[i] == 0) ? 0.0
						: (serviceTimeOnCloud[i] / (double) completedTaskOnCloud[i]);
				double _processingTimeOnCloud = (completedTaskOnCloud[i] == 0) ? 0.0
						: (processingTimeOnCloud[i] / (double) completedTaskOnCloud[i]);
				String genericResult3 = Integer.toString(completedTaskOnCloud[i]) + SimSettings.DELIMITER
						+ Integer.toString(failedTaskOnCloud[i]) + SimSettings.DELIMITER
						+ Integer.toString(uncompletedTaskOnCloud[i]) + SimSettings.DELIMITER
						+ Integer.toString(failedTaskDuetoWanBw[i]) + SimSettings.DELIMITER
						+ Double.toString(_serviceTimeOnCloud) + SimSettings.DELIMITER
						+ Double.toString(_processingTimeOnCloud) + SimSettings.DELIMITER 
						+ Double.toString(_wanDelay);

				appendToFile(genericBWs[i], genericResult1);
				appendToFile(genericBWs[i], genericResult2);
				appendToFile(genericBWs[i], genericResult3);
			}

			// close open files
			if (SimSettings.getInstance().getDeepFileLoggingEnabled()) {
				successBW.close();
				failBW.close();
			}
			vmLoadBW.close();
			locationBW.close();
			for (int i = 0; i < numOfAppTypes + 1; i++) {
				if (i < numOfAppTypes) {
					// if related app is not used in this simulation, just
					// discard it
					if (SimSettings.getInstance().getTaskLookUpTable()[i][0] == 0)
						continue;
				}
				genericBWs[i].close();
			}
		}

		//FileWriter fw = new FileWriter("Result.txt",true);
		//PrintWriter printWriter = new PrintWriter(fw);
		
		
		// printout important results
		printLine("# of tasks (Cloudlet/Cloud): "
				+ (failedTask[numOfAppTypes] + completedTask[numOfAppTypes]) + "("
				+ (failedTaskOnCloudlet[numOfAppTypes] + completedTaskOnCloudlet[numOfAppTypes]) + "/" 
				+ (failedTaskOnCloud[numOfAppTypes]+ completedTaskOnCloud[numOfAppTypes]) + ")");
		
		double tTasks = failedTaskOnCloudlet[numOfAppTypes] + completedTaskOnCloudlet[numOfAppTypes]+failedTaskOnCloud[numOfAppTypes]+completedTaskOnCloud[numOfAppTypes];
		
		setToatTasks(tTasks);
		
		/*
		printWriter.println("# of tasks (Cloudlet/Cloud): "
				+ (failedTask[numOfAppTypes] + completedTask[numOfAppTypes]) + "("
				+ (failedTaskOnCloudlet[numOfAppTypes] + completedTaskOnCloudlet[numOfAppTypes]) + "/" 
				+ (failedTaskOnCloud[numOfAppTypes]+ completedTaskOnCloud[numOfAppTypes]) + ")");
		*/
		
		printLine("# of failed tasks (Cloudlet/Cloud): "
				+ failedTask[numOfAppTypes] + "("
				+ failedTaskOnCloudlet[numOfAppTypes]
				+ "/" + failedTaskOnCloud[numOfAppTypes] + ")");
		
		//SimSettings.getInstance().getOrchestratorPolicies()
		
		
		double ftp = ((double) failedTask[numOfAppTypes] * (double) 100)/ (double) (completedTask[numOfAppTypes] + failedTask[numOfAppTypes]);
		
		double proTP = ((double) failedTaskOnCloudlet[numOfAppTypes] * (double) 100)/ (double) (completedTask[numOfAppTypes] + failedTask[numOfAppTypes]);
		
		setFailTaskPercent(ftp);
		
		setOnlyEdgefailedTask(proTP);
		
		
		/*
		printWriter.println("percentage of failed tasks: "
				+ String.format("%.6f", ((double) failedTask[numOfAppTypes] * (double) 100)
						/ (double) (completedTask[numOfAppTypes] + failedTask[numOfAppTypes]))
				+ "%");
		
		*/
		
		printLine("# of completed tasks (Cloudlet/Cloud): "
				+ completedTask[numOfAppTypes] + "("
				+ completedTaskOnCloudlet[numOfAppTypes]
				+ "/" + completedTaskOnCloud[numOfAppTypes] + ")");
		
		printLine("# of uncompleted tasks (Cloudlet/Cloud): "
				+ uncompletedTask[numOfAppTypes] + "("
				+ uncompletedTaskOnCloudlet[numOfAppTypes]
				+ "/" + uncompletedTaskOnCloud[numOfAppTypes] + ")");
		
		printLine("# of failed tasks due to vm capacity/LAN bw/WAN bw/mobility: "
				+ rejectedTaskDoToVmCapacity[numOfAppTypes]
				+ "/" + +failedTaskDuetoLanBw[numOfAppTypes] 
				+ "/" + +failedTaskDuetoWanBw[numOfAppTypes] 
				+ "/" + failedTaskDuetoMobility[numOfAppTypes]);
		
		printLine("percentage of failed tasks: "
				+ String.format("%.6f", ((double) failedTask[numOfAppTypes] * (double) 100)
						/ (double) (completedTask[numOfAppTypes] + failedTask[numOfAppTypes]))
				+ "%");
        setFailedTask(((double) failedTask[numOfAppTypes] * (double) 100) / (double) (completedTask[numOfAppTypes] + failedTask[numOfAppTypes]));
        
		printLine("average service time: "
				+ String.format("%.6f", serviceTime[numOfAppTypes] / (double) completedTask[numOfAppTypes])
				+ " seconds. (" + "on Cloudlet: "
				+ String.format("%.6f", serviceTimeOnCloudlet[numOfAppTypes] / (double) completedTaskOnCloudlet[numOfAppTypes])
				+ ", " + "on Cloud: "
				+ String.format("%.6f", serviceTimeOnCloud[numOfAppTypes] / (double) completedTaskOnCloud[numOfAppTypes])
				+ ")");

		printLine("average processing time: "
				+ String.format("%.6f", processingTime[numOfAppTypes] / (double) completedTask[numOfAppTypes])
				+ " seconds. (" + "on Cloudlet: "
				+ String.format("%.6f", processingTimeOnCloudlet[numOfAppTypes] / (double) completedTaskOnCloudlet[numOfAppTypes])
				+ ", " + "on Cloud: " 
				+ String.format("%.6f", processingTimeOnCloud[numOfAppTypes] / (double) completedTaskOnCloud[numOfAppTypes])
				+ ")");

		
		double prcsTime = processingTime[numOfAppTypes] / (double) completedTask[numOfAppTypes];
		
		setProcessingTime(prcsTime);
		
		printLine("average netwrok delay: "
				+ String.format("%.6f", networkDelay[numOfAppTypes] / (double) completedTask[numOfAppTypes])
				+ " seconds. (" + "LAN delay: "
				+ String.format("%.6f", lanDelay[numOfAppTypes] / (double) completedTaskOnCloudlet[numOfAppTypes])
				+ ", " + "WAN delay: "
				+ String.format("%.6f", wanDelay[numOfAppTypes] / (double) completedTaskOnCloud[numOfAppTypes]) + ")");

		printLine("average server utilization: " 
				+ String.format("%.6f", totalVmLoad / (double) vmLoadList.size()) + "%");
		
		printLine("average cost: " + cost[numOfAppTypes] / completedTask[numOfAppTypes] + "$");
		
		
		double netDly = networkDelay[numOfAppTypes]/(double) completedTask[numOfAppTypes];
		
		setNetDelay(netDly);
		
		
		double lanLatency = lanDelay[numOfAppTypes] / (double) completedTaskOnCloudlet[numOfAppTypes] ;
		
		setLanDelay(lanLatency);
		
		
		//printWriter.close();

		// clear related collections (map list etc.)
		taskMap.clear();
		vmLoadList.clear();
	}
}

class VmLoadLogItem {
	private double time;
	private double vmLoad;

	VmLoadLogItem(double _time, double _vmLoad) {
		time = _time;
		vmLoad = _vmLoad;
	}

	public double getLoad() {
		return vmLoad;
	}

	public String toString() {
		return time + SimSettings.DELIMITER + vmLoad;
	}
}

class LogItem {
	  SimLogger.TASK_STATUS status;
	private int initialDC;
	private int datacenterId;
	private int execDC;
	private int hostId;
	private int vmId;
	private int vmType;
	private int taskType;
	private int taskLenght;
	private int taskInputType;
	private int taskOutputSize;
	private double taskStartTime;
	private double taskSubmissionTime;
	private double processingStartTime;
	private double taskTransferTime;
	private double taskEndTime;
	private double taskDownloadTime;
	private double taskUploadTime;
	private double networkDelay;
	private double bwCost;
	private double cpuCost;
	private boolean isInWarmUpPeriod;

	LogItem(double _taskStartTime, int _taskType, int _taskLenght, int _taskInputType, int _taskOutputSize, int _taskHost, int _vmId) {
		taskStartTime = _taskStartTime;
		taskType = _taskType;
		taskLenght = _taskLenght;
		taskInputType = _taskInputType;
		taskOutputSize = _taskOutputSize;
		hostId = _taskHost;
		status = SimLogger.TASK_STATUS.CREATED;
		taskEndTime = 0;
		vmId = _vmId;

		if (_taskStartTime < SimSettings.getInstance().getWarmUpPeriod())
			isInWarmUpPeriod = true;
		else
			isInWarmUpPeriod = false;
	}
	
	
	public int getInitialDC() {
		return initialDC;
	}

	public void setInitDC(int initialDC) {
		this.initialDC = initialDC;
	}

	public int getExecDC() {
		return execDC;
	}

	public void setExecDC(int execDC) {
		this.execDC = execDC;
	}

	public double getSubmissionTime() {
		return taskSubmissionTime;
	}
	
	public double getProcStartTime() {
		return processingStartTime;
	}
	
	public double getDownloadTime() {
		return taskDownloadTime;
	}
	public double getUploadTime() {
		return taskUploadTime;
	}
	public double getTransferTime() {
		return taskTransferTime;
	}
	
	public double getEndTime() {
		return taskEndTime;
	}
	
	public void taskUploadStarted(double taskUploadTime) {
		networkDelay += taskUploadTime;
		status = SimLogger.TASK_STATUS.UPLOADING;
	}

	public void taskUploaded(int _datacenterId, int _hostId, int _vmId, int _vmType, double time) {
		status = SimLogger.TASK_STATUS.PROCESSING;
		datacenterId = _datacenterId;
		hostId = _hostId;
		vmId = _vmId;
		vmType = _vmType;
		processingStartTime = time;
	}

	public void taskDownloadStarted(double taskDownloadTime) {
		networkDelay += taskDownloadTime;
		status = SimLogger.TASK_STATUS.DOWNLOADING;
	}

	public void taskDownloaded(double _taskEndTime) {
		taskEndTime = _taskEndTime;
		status = SimLogger.TASK_STATUS.COMLETED;
	}

	public void taskRejectedDueToVMCapacity(double _taskRejectTime) {
		taskEndTime = _taskRejectTime;
		status = SimLogger.TASK_STATUS.REJECTED_DUE_TO_VM_CAPACITY;
	}

	public void taskRejectedDueToBandwidth(double _taskRejectTime, int _vmType) {
		vmType = _vmType;
		taskEndTime = _taskRejectTime;
		status = SimLogger.TASK_STATUS.REJECTED_DUE_TO_BANDWIDTH;
	}

	public void taskFailedDueToBandwidth(double _time) {
		taskEndTime = _time;
		status = SimLogger.TASK_STATUS.UNFINISHED_DUE_TO_BANDWIDTH;
	}

	public void taskFailedDueToMobility(double _time) {
		taskEndTime = _time;
		status = SimLogger.TASK_STATUS.UNFINISHED_DUE_TO_MOBILITY;
	}

	public void setCost(double _bwCost, double _cpuCos) {
		bwCost = _bwCost;
		cpuCost = _cpuCos;
	}

	public boolean isInWarmUpPeriod() {
		return isInWarmUpPeriod;
	}

	public double getCost() {
		return bwCost + cpuCost;
	}
	
	public int getHostID () {
		return hostId;
	}

	public double getNetworkDelay() {
		return networkDelay;
	}

	public double getServiceTime() {
		return taskEndTime - taskStartTime;
	}

	public SimLogger.TASK_STATUS getStatus() {
		return status;
	}

	public int getVmType() {
		return vmType;
	}

	
	public int getVmId() {
		return vmId;
	}


	public void setVmId(int vmId) {
		this.vmId = vmId;
	}
	
	public int getTaskType() {
		return taskType;
	}

	public String toString(int taskId) {
		String result = taskId + SimSettings.DELIMITER + datacenterId + SimSettings.DELIMITER + hostId
				+ SimSettings.DELIMITER + vmId + SimSettings.DELIMITER + vmType + SimSettings.DELIMITER + taskType
				+ SimSettings.DELIMITER + taskLenght + SimSettings.DELIMITER + taskInputType + SimSettings.DELIMITER
				+ taskOutputSize + SimSettings.DELIMITER + taskStartTime + SimSettings.DELIMITER + taskEndTime
				+ SimSettings.DELIMITER;

		if (status == SimLogger.TASK_STATUS.COMLETED)
			result += networkDelay;
		else if (status == SimLogger.TASK_STATUS.REJECTED_DUE_TO_VM_CAPACITY)
			result += "1"; // failure reason 1
		else if (status == SimLogger.TASK_STATUS.REJECTED_DUE_TO_BANDWIDTH)
			result += "2"; // failure reason 2
		else if (status == SimLogger.TASK_STATUS.UNFINISHED_DUE_TO_BANDWIDTH)
			result += "3"; // failure reason 3
		else if (status == SimLogger.TASK_STATUS.UNFINISHED_DUE_TO_MOBILITY)
			result += "4"; // failure reason 4
		else
			result += "0"; // default failure reason
		return result;
	}
}