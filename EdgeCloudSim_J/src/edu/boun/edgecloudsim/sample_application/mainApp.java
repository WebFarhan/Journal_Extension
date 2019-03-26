/*
 * Title:        EdgeCloudSim - Sample Application
 * 
 * Description:  Sample application for EdgeCloudSim
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.sample_application;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;

import edu.boun.edgecloudsim.core.ScenarioFactory;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_client.MobileDeviceManager;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

public class mainApp {
	
	/**
	 * Creates main() to run this example
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		//disable console output of cloudsim library
		Log.disable();
		
		//enable console ourput and file output of this application
		SimLogger.enablePrintLog();
		
		int iterationNumber = 1;
		String configFile = "";
		String outputFolder = "";
		String edgeDevicesFile = "";
		String applicationsFile = "";
		ArrayList<Double> allCounters = new ArrayList<>();
		ArrayList<Double> prcstimeCounter = new ArrayList<>();
		
		ArrayList<Double> netDelCounter = new ArrayList<>();
		
		ArrayList<Double> netLanDelayCounter = new ArrayList<>();
		
		ArrayList<Double> onlyEdgeFail = new ArrayList<>();
		
		ArrayList<Double> allTasks = new ArrayList<>();
		ArrayList<Double> failTasks = new ArrayList<>();
		ArrayList<Double> meanContainer = new ArrayList<>();
		ArrayList<Double> stdContainer = new ArrayList<>();
		
		
		
		if (args.length == 3){
			configFile = args[0];
			edgeDevicesFile = args[1];
			applicationsFile = args[2];
			//outputFolder = args[3];
			//rm iterationNumber = Integer.parseInt(args[4]);
		}
		else{
			SimLogger.printLine("Simulation setting file, output folder and iteration number are not provided! Using default ones...");
			configFile = "EdgeCloudSim_J/scripts/sample_application/config/default_config.properties";
			applicationsFile = "EdgeCloudSim_J/scripts/sample_application/config/applications50p.xml";
			edgeDevicesFile = "EdgeCloudSim_J/scripts/sample_application/config/edge_devices.xml";
			outputFolder = "EdgeCloudSim_J/sim_results/ite" + iterationNumber;
		}

		
		//outputFolder = "/work/razin/AnnaV2I_Fall18/TestOutPut/ite" + iterationNumber;
		//load settings from configuration file
		SimSettings SS = SimSettings.getInstance();
		if(SS.initialize(configFile, edgeDevicesFile, applicationsFile) == false){
			SimLogger.printLine("cannot initialize simulation settings!");
			System.exit(0);
		}
		
		if(SS.getFileLoggingEnabled()){
			SimLogger.enableFileLog();
			SimUtils.cleanOutputFolder(outputFolder);
		}
		
		DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		Date SimulationStartDate = Calendar.getInstance().getTime();
		String now = df.format(SimulationStartDate);
		SimLogger.printLine("Simulation started at " + now);
		SimLogger.printLine("----------------------------------------------------------------------");
		
		ArrayList<Double> arrli1;

		for(int j=SS.getMinNumOfMobileDev(); j<=SS.getMaxNumOfMobileDev(); j+=SS.getMobileDevCounterSize())
		{
			for(int k=0; k<SS.getSimulationScenarios().length; k++)
			{
				for(int i=0; i<SS.getOrchestratorPolicies().length; i++)
				{
					arrli1 = new ArrayList<Double>(SS.getOrchestratorPolicies().length);
					
					
					String simScenario = SS.getSimulationScenarios()[k];
					String orchestratorPolicy = SS.getOrchestratorPolicies()[i];
					Date ScenarioStartDate = Calendar.getInstance().getTime();
					now = df.format(ScenarioStartDate);
					
					FileWriter fw = new FileWriter("Result.txt",true);
					PrintWriter printWriter = new PrintWriter(fw);
					
					
					SimLogger.printLine("Scenario started at " + now);
					SimLogger.printLine("Scenario: " + simScenario + " - Policy: " + orchestratorPolicy + " - #iteration: " + iterationNumber);
					SimLogger.printLine("Duration: " + SS.getSimulationTime()/3600 + " hour(s) - Poisson: " + SS.getTaskLookUpTable()[0][2] + " - #devices: " + j);
					SimLogger.getInstance().simStarted(outputFolder,"SIMRESULT_" + simScenario + "_"  + orchestratorPolicy + "_" + j + "DEVICES");
					
					printWriter.println("Scenario started at " + now);
							
					printWriter.println();
					
					
					for(int loopIndex =0;loopIndex<5;loopIndex++) {
					
					try
					{
						// First step: Initialize the CloudSim package. It should be called
						// before creating any entities.
						int num_user = 2;   // number of grid users
						Calendar calendar = Calendar.getInstance();
						boolean trace_flag = false;  // mean trace events
				
						// Initialize the CloudSim library
						CloudSim.init(num_user, calendar, trace_flag, 0.01);
						
						// Generate EdgeCloudsim Scenario Factory
						ScenarioFactory sampleFactory = new SampleScenarioFactory(j,SS.getSimulationTime(), orchestratorPolicy, simScenario,j);
						
						// Generate EdgeCloudSim Simulation Manager
						SimManager manager = new SimManager(sampleFactory, j, simScenario);
						
						// Start simulation
						manager.startSimulation();
					}
					catch (Exception e)
					{
						SimLogger.printLine("The simulation has been terminated due to an unexpected error");
						e.printStackTrace();
						System.exit(0);
					}
					
					Date ScenarioEndDate = Calendar.getInstance().getTime();
					now = df.format(ScenarioEndDate);
					SimLogger.printLine("Scenario finished at " + now +  ". It took " + SimUtils.getTimeDifference(ScenarioStartDate,ScenarioEndDate));
					SimLogger.printLine("----------------------------------------------------------------------");
				
					double d = SimLogger.getInstance().getFailedTask();
					arrli1.add(d);
					
					printWriter.println("Total Number of Tasks :"+SimLogger.getInstance().getToatTasks());
					printWriter.println("Percentage of failed task in EdgeCloudSim : "+SimLogger.getInstance().getFailTaskPercent());
					
					SimLogger.printLine("Number failed tasks "+ SimLogger.getInstance().getDlMisCounter());
					printWriter.println("Number failed tasks "+ SimLogger.getInstance().getDlMisCounter());
					printWriter.println();
					printWriter.println("Smart Oil task processing finished!");
					printWriter.println("*************************************************");
					
					
					double rsPr = (SimLogger.getInstance().getDlMisCounter()/ SimLogger.getInstance().getToatTasks())*100;
					
					allCounters.add(SimLogger.getInstance().getFailTaskPercent());
					
					allTasks.add(SimLogger.getInstance().getToatTasks());
					
					onlyEdgeFail.add(SimLogger.getInstance().getOnlyEdgefailedTask());
					
					prcstimeCounter.add(SimLogger.getInstance().getProcessingTime());
					
					netDelCounter.add(SimLogger.getInstance().getNetDelay());
					
					netLanDelayCounter.add(SimLogger.getInstance().getLanDelay());
					
					
					failTasks.add(rsPr);
					
					
					}//simulation loop
					//SimLogger.printLine(" failed list "+arrli1);
					//arrli1.clear();
					
					int arrSize = failTasks.size();
					double sumDl = 0;
					double resultDl =0;
					
					for(int m=0;m<arrSize;m++) {
						sumDl = sumDl + failTasks.get(m);
						
					}
					
					resultDl = (sumDl/arrSize);
					
					double stdDl = 0.0;
					
					for (int p = 0; p <arrSize; p++)
					{
						
						stdDl += Math.pow((failTasks.get(p) - resultDl), 2) / (arrSize);
					}
					
					double STD = Math.sqrt(stdDl);
					
					
					SimLogger.printLine("Percentage of task failed due to deadline miss "+resultDl);
										
					double sizeOfArray = allCounters.size();
					double sum = 0;
					double result2 = 0;
					
					SimLogger.printLine("Array Size "+ sizeOfArray);
					
					for(int x=0;x<sizeOfArray;x++)
					{
						sum = sum + allCounters.get(x);
						
					}
					
					result2 = (sum/sizeOfArray);
					
					double sd = 0;
					for (int indx = 0; indx <sizeOfArray; indx++)
					{
						
					    sd += Math.pow((allCounters.get(indx) - result2), 2) / (sizeOfArray);
					}
					double standardDeviation = Math.sqrt(sd);
					
					
					
					int edgeFailarrSize = onlyEdgeFail.size();
					double edgeFailSum = 0;
					double edgeFailResult = 0;
					
					for(int x=0;x<edgeFailarrSize;x++)
					{
						edgeFailSum = edgeFailSum + onlyEdgeFail.get(x);
						
					}
					
					
					edgeFailResult = (edgeFailSum/edgeFailarrSize);
					
					double edgeFailSD = 0;
					
					for (int p = 0; p <edgeFailarrSize; p++)
					{
						
						edgeFailSD += Math.pow((onlyEdgeFail.get(p) - edgeFailResult), 2) / (edgeFailarrSize);
					}
					
					double edgeSTD = Math.sqrt(edgeFailSD);
					
					//////////////////////////////////////
					
					int prcTimeArrSize = prcstimeCounter.size();
					double sumPrcs = 0;
					double resultPrcs =0;
					
					
					for(int x=0;x<prcTimeArrSize;x++) {
						sumPrcs = sumPrcs + prcstimeCounter.get(x);
						
					}
					
					resultPrcs = (sumPrcs/prcTimeArrSize);
					
					double stdPrcs = 0.0;
					
					for (int ph = 0; ph <prcTimeArrSize; ph++)
					{
						
						stdPrcs += Math.pow((prcstimeCounter.get(ph) - resultPrcs), 2) / (prcTimeArrSize);
					}
					
					double STDPrcs = Math.sqrt(stdPrcs);
					
					////////////////////Network Delay /////////////////////////
					
					
					int netDelArrSize = netDelCounter.size();
					double sumNetDel = 0;
					double rsltNetDel = 0;
					
					for(int n = 0;n<netDelArrSize;n++) {
						
						sumNetDel = sumNetDel+netDelCounter.get(n);
					}
					
					rsltNetDel = (sumNetDel/netDelArrSize);
					
					double stdNetDel = 0.0;
					
					for(int q = 0;q<netDelArrSize;q++) {
						
						stdNetDel += Math.pow((netDelCounter.get(q) - rsltNetDel), 2) / (netDelArrSize);
						
					}
					
					double STDnetDel = Math.sqrt(stdNetDel);
					
					
					////////////////////LAN Delay /////////////////////////
					
					int lanDelArrSize = netLanDelayCounter.size();
					double sumLanDel = 0;
					double rsltLanDel = 0;
					
					for(int m = 0;m<lanDelArrSize;m++) {
						
						sumLanDel = sumLanDel+netLanDelayCounter.get(m);
					}
					
					rsltLanDel = sumLanDel/lanDelArrSize;
					
					double varLanDel = 0.0; 
					
					for(int p = 0;p<lanDelArrSize;p++) {
						
						varLanDel += Math.pow((netLanDelayCounter.get(p) - rsltLanDel), 2) / (lanDelArrSize);
						
					}
					
					double STDLanDel = Math.sqrt(varLanDel);
					
					////////////////////LAN Delay /////////////////////////
					
					
					SimLogger.printLine("Average of running simulation : "+ result2);
					
					printWriter.println("Scenario: " + simScenario + " - Policy: " + orchestratorPolicy + " - #iteration: " + iterationNumber);
					printWriter.println("Duration: " + SS.getSimulationTime()/3600 + " hour(s) - Poisson: " + SS.getTaskLookUpTable()[0][2] + " - #devices: " + j);
					
					SimLogger.printLine("############# Average of running simulation : "+ result2);
					SimLogger.printLine("############# Standard Deviation : "+standardDeviation);
					
					
					printWriter.println("Average Percentage of task failed due to deadline miss :"+resultDl);
					printWriter.println("Standard Deviation in deadline miss data :"+STD);
					printWriter.println();
					printWriter.println("############# Failed tasks in simulation loop :"+failTasks);
					printWriter.println("############# Failed tasks according to EdgeCloudSim in simulation loop :"+allCounters);
					
					
					printWriter.println(" ************ Only Edge Failed Tasks  : "+onlyEdgeFail);
					printWriter.println(" ************ Average Edge Failed Tasks  : "+edgeFailResult);
					printWriter.println(" ************ Standard Deviation of Edge Failed Tasks : "+edgeSTD);
					
					
					printWriter.println("############# Average of running simulation : "+ result2);
					printWriter.println("############# Standard Deviation : "+standardDeviation);
					
					printWriter.println("############# Average of processing time : "+ resultPrcs);
					printWriter.println("############# Standard Deviation processing time : "+STDPrcs);
					
					printWriter.println("############# Average of Network Delay : "+ rsltNetDel);
					printWriter.println("############# Standard Deviation Network Delay : "+STDnetDel);
					
					printWriter.println("############# Average of LAN Delay : "+ rsltLanDel);
					printWriter.println("############# Standard Deviation LAN Delay : "+ STDLanDel);
					
					/*
					SimLogger.printLine("$$$$$$Task completion time of tak type 1 in edge 0 ="+SimLogger.getInstance().matrix.getMu(0, 1));
					SimLogger.printLine("$$$$$$Task completion time of tak type 1 in edge 1 ="+SimLogger.getInstance().matrix.getMu(1, 1));
					SimLogger.printLine("$$$$$$Task completion time of tak type 1 in edge 2 ="+SimLogger.getInstance().matrix.getMu(2, 1));
					SimLogger.printLine("$$$$$$Task completion time of tak type 1 in edge 3 ="+SimLogger.getInstance().matrix.getMu(3, 1));
					SimLogger.printLine("$$$$$$Task completion time of tak type 1 in edge 4 ="+SimLogger.getInstance().matrix.getMu(4, 1));
					
					SimLogger.printLine("");
					SimLogger.printLine("");
					
					SimLogger.printLine("$$$$$$Task completion time of tak type 2 in edge 0 ="+SimLogger.getInstance().matrix.getMu(0, 2));
					SimLogger.printLine("$$$$$$Task completion time of tak type 2 in edge 1 ="+SimLogger.getInstance().matrix.getMu(1, 2));
					SimLogger.printLine("$$$$$$Task completion time of tak type 2 in edge 2 ="+SimLogger.getInstance().matrix.getMu(2, 2));
					SimLogger.printLine("$$$$$$Task completion time of tak type 2 in edge 3 ="+SimLogger.getInstance().matrix.getMu(3, 2));
					SimLogger.printLine("$$$$$$Task completion time of tak type 2 in edge 4 ="+SimLogger.getInstance().matrix.getMu(4, 2));
					*/
					
					
					
					
					//meanContainer.add(result2);
					//stdContainer.add(standardDeviation);
					
					printWriter.println();
					printWriter.close();
					
					onlyEdgeFail.clear();
					allCounters.clear();
					allTasks.clear();
					failTasks.clear();
					prcstimeCounter.clear();
					netDelCounter.clear();
					netLanDelayCounter.clear();
					
				}//End of orchestrators loop
				
			
			}//End of scenarios loop
		}//End of mobile devices loop

		Date SimulationEndDate = Calendar.getInstance().getTime();
		now = df.format(SimulationEndDate);
		SimLogger.printLine("Simulation finished at " + now +  ". It took " + SimUtils.getTimeDifference(SimulationStartDate,SimulationEndDate));
	}
}
