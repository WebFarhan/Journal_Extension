/*
 * Title:        EdgeCloudSim - Basic Edge Orchestrator implementation
 * 
 * Description: 
 * BasicEdgeOrchestrator implements basic algorithms which are
 * first/next/best/worst/random fit algorithms while assigning
 * requests to the edge devices.
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_orchestrator;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.VmSchedulerSpaceShared;
import org.cloudbus.cloudsim.core.CloudSim;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.util.concurrent.AbstractScheduledService.Scheduler;
import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils.Collections;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_server.EdgeHost;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.utils.ETCMatrix;
import edu.boun.edgecloudsim.utils.ETTMatrix;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.PTCMatrix;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

public class BasicEdgeOrchestrator extends EdgeOrchestrator {
	private int numberOfHost; //used by load balancer
	@SuppressWarnings("unused")
	private int lastSelectedHostIndex; //used by load balancer
	private int[] lastSelectedVmIndexes; //used by each host individually
	private static Datacenter receivingBS; // !!! IMPORTANT !!! DON'T USE THE METHOD Datacenter.getId(), it's messed up, use recBS instead if u need an index.
	private static int recBS = -1; //Receiving DC ID
	private static int redirectBS = 0;
	private static String basicEdgePlociy;
	private static ArrayList<Task> tempList = new ArrayList<Task>();
	
	public static String getBasicEdgePlociy() {
		return basicEdgePlociy;
	}

	public static void setBasicEdgePlociy(String basicEdgePlociy) {
		BasicEdgeOrchestrator.basicEdgePlociy = basicEdgePlociy;
	}

	public static int getRedirectBS() {
		return redirectBS;
	}

	public static void setRedirectBS(int redirectBS) {
		BasicEdgeOrchestrator.redirectBS = redirectBS;
	}

	private static int bsIndex;
	private static int edgeCloudDCType1 = 1, edgeCloudDCType2 = 1;
	public static boolean flagEdge1 = false;
	public static boolean flagEdge2 = false;
	
	private CloudletSchedulerSpaceShared cloudletScheduler1;
	
	public static int urgentTaskC = 0;
	public static int getUrgentTaskC() {
		return urgentTaskC;
	}
    
	public static ArrayList<Task> getTempList(){
		return tempList;
	}
	
	public Task getElementTempList(int indx) {
		return tempList.get(indx);
	}
	
	public static void clearTemplist() {
		
		tempList.clear();
	}
	
	public int getTempListSize() {
		return tempList.size();
	}
	
	public void setUrgentTaskC(int urgentTaskC) {
		this.urgentTaskC = urgentTaskC;
	}

	public static int nonUrgTaskC = 0;
	
	public static int getNonUrgTaskC() {
		return nonUrgTaskC;
	}

	public void setNonUrgTaskC(int nonUrgTaskC) {
		this.nonUrgTaskC = nonUrgTaskC;
	}

	private ArrayListMultimap<String, Double> ptcTimes = ArrayListMultimap.create();
	
	public static int getRecBS() {
		return recBS;
	}

	public void setRecBS(int recBS) {
		this.recBS = recBS;
	}

	private ArrayList<Integer> neighboringBS = new ArrayList<>(); //Neighboring BaseStations
	
	private static ETCMatrix matrix;
	private static ETTMatrix ettmatrix;
	private static PTCMatrix ptcmatrix;
	
	public BasicEdgeOrchestrator(String _policy, String _simScenario, String _schedAlgo) {
		super(_policy, _simScenario,_schedAlgo);
	}

	@Override
	public void initialize() {
		numberOfHost=SimSettings.getInstance().getNumOfEdgeHosts();
		
		if(policy.equalsIgnoreCase("EdgeCloud")) {
			
			setBasicEdgePlociy("EdgeCloud");
		}
		else if(policy.equalsIgnoreCase("MECT")) {
			
			setBasicEdgePlociy("MECT");
		}
		else if(policy.equalsIgnoreCase("Probability")) {
		
			setBasicEdgePlociy("Probability");
		}
		else if(policy.equalsIgnoreCase("Certainty")) {
			
			setBasicEdgePlociy("Certainty");
		}
		
		if(schedAlgo.equalsIgnoreCase("RR")) {
			
		}
		
		
		lastSelectedHostIndex = -1;
		lastSelectedVmIndexes = new int[numberOfHost];
		for(int i=0; i<numberOfHost; i++)
			lastSelectedVmIndexes[i] = -1;
	}
	
	
	
	
	
	private static double getConvolveProbability(double mu, double sigma, double deadLine) {
		
		try {
		NormalDistribution distr = new NormalDistribution(mu, sigma);
		return distr.cumulativeProbability(deadLine);
		}catch(NotStrictlyPositiveException exc) {
			return 0.0;
		}
	
	}
	

	@Override
	public int getDeviceToOffload(Task task) {
		
		int result = SimSettings.GENERIC_EDGE_DEVICE_ID;
		matrix = SimLogger.getInstance().matrix;
		double dl = 0.0;
		
		
			if(task.getUrgentFlage()== 0) {
				result = SimSettings.CLOUD_DATACENTER_ID;
				nonUrgTaskC++;
				setNonUrgTaskC(nonUrgTaskC);
			}
			else {
				
				if(policy.equalsIgnoreCase("EdgeCloud")) {
				
					result = SimSettings.EDGE_ORCHESTRATOR_ID;
				}
				else result = SimSettings.GENERIC_EDGE_DEVICE_ID;
				
				urgentTaskC++;
				setUrgentTaskC(urgentTaskC);
				/* Calculate the location of receiving Base Station
				 * the closest one to the device(vehicle)
				 */
				
				Location deviceLoc = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(), CloudSim.clock());
				int xdev = deviceLoc.getXPos();// mobile device x position
				int ydev = deviceLoc.getYPos();// mobile device y position
				
			
				List<Datacenter> datacenters = SimManager.getInstance().edgeServerManager.getDatacenterList();
				
				double best = 1000;
		
				for(int i = 0; i < datacenters.size(); i++) {
					List<EdgeHost> hostlist = datacenters.get(i).getHostList();
															
					for(EdgeHost host : hostlist) {
						Location hostLocation = host.getLocation();
						int xhost = hostLocation.getXPos(); // Edge system x position
						int yhost = hostLocation.getYPos();// Edge System y position
						double dist = Math.sqrt((Math.pow((double)xdev-xhost, 2))+ (Math.pow((double)ydev-yhost, 2)));
						if (dist <= best) {
							best = dist;
							setReceivingBS(datacenters.get(i));
							recBS = i;
							task.setDc(i);
							dl = SimManager.getInstance().getEdgeOrchestrator().deadline(task, matrix, 0.0001,recBS);
							}
						}
					}
				
				if(policy.equalsIgnoreCase("EdgeCloud")) {
					
					if (flagEdge1 == false) {
						
						edgeCloudDCType1 = recBS;
						flagEdge1 = true;
					}
				
				}
				
			}
		
		return result;
	}
	
	@Override
	public EdgeVM getVmToOffload(Task task) {
		if(simScenario.equals("TWO_TIER_WITH_EO")) {

			return selectVmOnLoadBalancer(task);
	
		}
		else
			return selectVmOnHost(task);
	}
	
	
	public static int availableVM(int selecDC, Task task) {
		
		
		Datacenter dc = SimManager.getInstance().edgeServerManager.getDatacenterList().get(selecDC);
		int avVM = 0;
		List<EdgeVM> vmArrList = dc.getVmList();
		
		for(int vmIndex=0; vmIndex<vmArrList.size(); vmIndex++){
			double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArrList.get(vmIndex).getVmType());
			double targetVmCapacity = (double)100 - vmArrList.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
			
			if(requiredCapacity <= targetVmCapacity){

				avVM++;
				
			}
		}
		
		return avVM;
	}
	
	/*
	 * The base case policy;
	 */
	
	public EdgeVM selectVmOnHost(Task task){
		
		EdgeVM selectedVM = null;
		List<EdgeVM> vmArray = receivingBS.getVmList();
		
		for(int i = 0; i < vmArray.size(); i++) {
			double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(i).getVmType());
			double targetVmCapacity = (double)100 - vmArray.get(i).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
			if(requiredCapacity <= targetVmCapacity)
				selectedVM = vmArray.get(i);
			}

		
		return selectedVM;
	}
	
	
	public EdgeVM selectVmOnCloudHost(Task task){
			
		EdgeVM selectedVM = null;
		for(int hostIndex=0; hostIndex<numberOfHost; hostIndex++){
			List<EdgeVM> vmArray = SimManager.getInstance().getLocalServerManager().getVmList(hostIndex);
			for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				
				if(requiredCapacity <= targetVmCapacity){
					selectedVM = vmArray.get(vmIndex);
					break;
				}
			}
		}

		return selectedVM;
	}
	
	
	/*
	 * Load Balancer Policy;
	 */

	public EdgeVM selectVmOnLoadBalancer(Task task){
		EdgeVM selectedVM = null;
		
		if(policy.equalsIgnoreCase("Probability")) {
			
				getNeighbors(task);
				selectedVM  = getDC(task);
						
		}
		else if(policy.equalsIgnoreCase("Baseline")){
		
			List<EdgeVM> vmArray =  receivingBS.getVmList();
			for(int i = 0; i < vmArray.size(); i++) {
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(i).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(i).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				if(requiredCapacity <= targetVmCapacity)
					selectedVM = vmArray.get(i);
			}
			
		}
		else if(policy.equalsIgnoreCase("MECT")) {
			
			Datacenter dcM = SimManager.getInstance().edgeServerManager.getDatacenterList().get(getMectDC(task));
			
			if(recBS != getMectDC(task)) {
				redirectBS++;
				setRedirectBS(redirectBS);
		     }
			
			
			List<EdgeVM> vmArrayMect = dcM.getVmList();
		    //tempList = new ArrayList<EdgeVM>();
			
			for(int i = 0; i < vmArrayMect.size(); i++) {
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArrayMect.get(i).getVmType());
				double targetVmCapacity = (double)100 - vmArrayMect.get(i).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				
				//System.out.println(" requiredCapacity : "+ requiredCapacity + "  targetVmCapacity  :"+ targetVmCapacity);
				if(requiredCapacity <= targetVmCapacity && vmArrayMect.get(i).getQueueSize()<8){
					selectedVM = vmArrayMect.get(i);
					selectedVM.allocateTasktoQue(task);
					System.out.println(" VM "+selectedVM.getId()+" current quesize "+selectedVM.getQueueSize());
					break;
				}
				else {
					tempList.add(task);
					//selectedVM = null;
					System.out.println(" Temp list current size "+tempList.size());
					System.out.println(" Task "+task.getCloudletId() +" has added to the temporary pool");
				}
								
			}
			
			//if(selectedVM!=null)
				//System.out.println(" MECT selected vm id " + selectedVM.getId() + " MECT selected vm host "+ selectedVM.getHost().getId());
			
		}
		else if(policy.equalsIgnoreCase("Certainty")) {
			
			Datacenter DCC = SimManager.getInstance().edgeServerManager.getDatacenterList().get(getCertainityDC(task));
			
			if(recBS != getCertainityDC(task)) {
				redirectBS++;
				setRedirectBS(redirectBS);
		     }
			
			List<EdgeVM> vmArrayCert = DCC.getVmList();
			
			for(int i = 0; i < vmArrayCert.size(); i++) {
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArrayCert.get(i).getVmType());
				double targetVmCapacity = (double)100 - vmArrayCert.get(i).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				if(requiredCapacity <= targetVmCapacity) {
					selectedVM = vmArrayCert.get(i);
					}
			}
			
			
		}
		else if(policy.equalsIgnoreCase("EdgeCloud")) {
			int avVM = 0;
			Datacenter eDC = SimManager.getInstance().edgeServerManager.getDatacenterList().get(edgeCloudDCType1);
			
			List<EdgeVM> vmArray = eDC.getVmList();
			
			//System.out.println(" vm array size "+vmArray.size());
			
			for(int vmIndex=0; vmIndex<vmArray.size(); vmIndex++){
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(vmIndex).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(vmIndex).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				
				if(requiredCapacity <= targetVmCapacity){
					selectedVM = vmArray.get(vmIndex);
					avVM++;
					//break;
				}
				
			}
			
				
			int vc = SimUtils.getRandomNumber(0, vmArray.size()-1);
					
					
		}
		
		return selectedVM;
		
	}
	
	
	public double[] getCI(double mean,double std, int numOfTasks) {
		
		
	double confidenceLevel = 1.96;
	double temp = confidenceLevel * std / (double)Math.sqrt(numOfTasks);
	
	//SimLogger.printLine("CI :"+(mean-temp)+" "+(mean+temp));
		
	return new double[]{mean - temp, mean + temp};
	}
	
		
	/*
	 * Calculate the deadline for the task
	 * 
	 */
	
	public double deadline(Task task, ETCMatrix b, double slack, int targetBS) {
		
		double comDelay = SimManager.getInstance().getNetworkModel().getUploadDelay(task.getMobileDeviceId(), targetBS ,task.getCloudletFileSize()) + 
							SimManager.getInstance().getNetworkModel().getDownloadDelay(targetBS, task.getMobileDeviceId(), task.getCloudletFileSize());
		double submissionTime = task.getSubmissionTime();
		double avgMu = 0;
		for(int i = 0; i < b.getDataCnum(); i++) {
			avgMu += b.getMu(i, task.getTaskType().ordinal());
		}
		double avgMuAll = avgMu/b.getDataCnum();
		double beta = 1.0;
		double alpha = 1.0;
		double deadline =  (beta*avgMuAll)+ slack + submissionTime + alpha*comDelay;
		task.setDeadLine(deadline);
		return deadline;
	}
	
	public EdgeVM getDC(Task task) {
		EdgeVM selectedVM = null;
		matrix = SimLogger.getInstance().matrix;
		ettmatrix = SimLogger.getInstance().ettMatrix;
		ptcmatrix = SimLogger.getInstance().ptcMatrix;
		
	    double dl1 = 0.0;
		double dl = SimManager.getInstance().getEdgeOrchestrator().deadline(task, matrix, 0.0001,recBS);
		double bestProb = matrix.getProbability(recBS, task.getTaskType().ordinal(), dl);
		
		double ciRecBS[] = getCI(matrix.getMu(recBS, task.getTaskType().ordinal()), matrix.getSigma(recBS, task.getTaskType().ordinal()), matrix.getNumOfTasks());
		
		double[] probContainer = new double[neighboringBS.size()]; 
		double[] neighCIL = new double[neighboringBS.size()];
		double[] neighCIU = new double[neighboringBS.size()];
		
		double[] tempCI;
		double convMu = 0.0;
		double convMuPTC = 0.0;
		double convVariance = 0.0;
		double conVarPTC = 0.0;
		
		for(int i = 0; i < neighboringBS.size(); i++) {
			
			convMu = matrix.getMu(neighboringBS.get(i),  task.getTaskType().ordinal())+ettmatrix.getMu(neighboringBS.get(i), task.getTaskType().ordinal());
			
			convVariance = Math.pow(matrix.getSigma(neighboringBS.get(i), task.getTaskType().ordinal()),2) + Math.pow(ettmatrix.getSigma(neighboringBS.get(i), task.getTaskType().ordinal()),2);
			
			dl1 = SimManager.getInstance().getEdgeOrchestrator().deadline(task, matrix, 0.0001,neighboringBS.get(i)); // 
			
			double prob = getConvolveProbability(convMu, Math.sqrt(convVariance), dl1); // probability of neighbor 
			
			tempCI = getCI(convMu, Math.sqrt(convVariance), matrix.getNumOfTasks());
			
			if(bestProb < prob) {
				probContainer[i] = prob;
				neighCIL[i] = tempCI[0];
				neighCIU[i] = tempCI[1];
			}
			
		int currentAvailableVm = availableVM(neighboringBS.get(i), task);
		
		//System.out.println(" Available vms in datacenter "+ neighboringBS.get(i)+ " is === "+ currentAvailableVm);
		
		}
		
		double temp;

		for (int i = 0; i < neighboringBS.size(); i++) 
		{
			for (int j = i + 1; j < neighboringBS.size(); j++) 
			{
				if (probContainer[i] < probContainer[j]) 
				{
					temp = probContainer[i];
					probContainer[i] = probContainer[j];
					probContainer[j] = temp;
					bsIndex = j;
				}
			}
		}
		

		
		if (probContainer[0] < bestProb) { // local wins
			
			List<EdgeVM> vmArray = SimManager.getInstance().edgeServerManager.getDatacenterList().get(recBS).getVmList();
			
	
			//finding the ptc matrix
			double ptcMu;
			double ptcProb;
			task.setDc(recBS);
			
			
			if(schedAlgo.equalsIgnoreCase("RR")) {
				Location deviceLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(), CloudSim.clock());
				//in our scenasrio, serving wlan ID is equal to the host id
				//because there is only one host in one place
				//int relatedHostId=deviceLocation.getServingWlanId();
				
				int tries = 0;
				while(tries < vmArray.size()){
					lastSelectedVmIndexes[recBS] = (lastSelectedVmIndexes[recBS]+1) % vmArray.size();
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(lastSelectedVmIndexes[recBS]).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(lastSelectedVmIndexes[recBS]).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					if(requiredCapacity <= targetVmCapacity){
						selectedVM = vmArray.get(lastSelectedVmIndexes[recBS]);
						break;
					}
					tries++;
				}
				
				
				
			}
			else {
				for(int j = 0; j < vmArray.size(); j++) {
				
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(j).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(j).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());

				ptcProb = ptcmatrix.getProbability(recBS, vmArray.get(j).getId(), task.getTaskType().ordinal(), dl);
				
				//System.out.println(" ptc Loacl prob : "+ptcProb+" datacenter prob : "+ bestProb);
				
				if(ptcProb > bestProb || (ptcProb == bestProb || task.getTaskPref() == vmArray.get(j).getVmChategory())){
								
					if(requiredCapacity <= targetVmCapacity) {
						selectedVM = vmArray.get(j);
						break;
					}
					
				}
			}
			
			}
			
			//end of scheduling policy
			
			if(selectedVM == null) {
				
				for(int j = 0; j < vmArray.size(); j++) {
					
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(j).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(j).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					
					if(requiredCapacity <= targetVmCapacity) {
						selectedVM = vmArray.get(j);
						//break;
					}
					else if(task.getTaskPref() == vmArray.get(j).getVmChategory()) {
						selectedVM = vmArray.get(j);
						break;
					}
				}
				
			}
			
		}
		else { // remote wins
			
			int dc = 0;
			int overlap = 0;
	
			if(neighboringBS.size()==1) {
				bsIndex = 0;
			}
			else { // check ci of pr1 with other neighbors
			
			   for(int li=0;li< neighboringBS.size();li++) {
				   
				   if(neighCIU[li] < neighCIL[bsIndex] || neighCIU[bsIndex] < neighCIL[li]) {
					   bsIndex = li;
					   break;
				   }
				  
			   }
			}
            
			
			dc = neighboringBS.get(bsIndex);
			task.setDc(dc);
			
			if(recBS != dc) {
				redirectBS++;
				setRedirectBS(redirectBS);
							
			}
			
			List<EdgeVM> vmArray = SimManager.getInstance().edgeServerManager.getDatacenterList().get(dc).getVmList();
			
			for(int j = 0; j < vmArray.size(); j++) {
				double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(j).getVmType());
				double targetVmCapacity = (double)100 - vmArray.get(j).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
				
				double ptcProbRemote = ptcmatrix.getProbability(dc, vmArray.get(j).getId(), task.getTaskType().ordinal(), dl);// need to check this part
				
				//System.out.println(" ptc remote prob : "+ptcProbRemote+" datacenter prob : "+ bestProb);
				
				if(ptcProbRemote > probContainer[0] || ptcProbRemote == probContainer[0] ) {
					if(requiredCapacity <= targetVmCapacity) {
						selectedVM = vmArray.get(j);
						break;
					}
				}
			}
			
			if(selectedVM == null) {
				
				for(int j = 0; j < vmArray.size(); j++) {
					
					double requiredCapacity = ((CpuUtilizationModel_Custom)task.getUtilizationModelCpu()).predictUtilization(vmArray.get(j).getVmType());
					double targetVmCapacity = (double)100 - vmArray.get(j).getCloudletScheduler().getTotalUtilizationOfCpu(CloudSim.clock());
					if(requiredCapacity <= targetVmCapacity) {
						selectedVM = vmArray.get(j);
						break;
					}

				}
				
			}
			
			//System.out.println(" waiting list @@@@@ "+vmArray.get(0).getCloudletScheduler().getCloudletWaitingList());
			bsIndex = 0;
		}
		
		neighboringBS.clear();
		
		
		System.out.println(" no of redirected tasks "+redirectBS);
		return selectedVM;
	}
	
	public int getMectDC(Task task) {
		
		ETCMatrix matrix = SimLogger.getInstance().matrix;
		int mectDC = recBS;
		double compValue = 99999;
		double tmpValue = 0;
		
		List<Datacenter> dcs = SimManager.getInstance().edgeServerManager.getDatacenterList();
		
		for(int i = 0; i < dcs.size(); i++) {
			tmpValue = matrix.getMu(i, task.getTaskType().ordinal());
			
			if(compValue > tmpValue) {
				compValue = tmpValue;
				mectDC = i;
				//double ddll = SimManager.getInstance().getEdgeOrchestrator().deadline(task, matrix, 0.0,mectDC);
				double mectComD = SimManager.getInstance().getNetworkModel().getUploadDelay(task.getMobileDeviceId(), mectDC ,task.getCloudletFileSize())+SimManager.getInstance().getNetworkModel().getDownloadDelay(mectDC, task.getMobileDeviceId(), task.getCloudletFileSize());
				
				//double last = ddll-mectComD;
				task.setDeadLine(task.getDeadLine()-mectComD);
				
			}
		}
		
		return mectDC;
	}
	
	public int getCertainityDC(Task task) {

		int bestDC = recBS;
		double crt = -99999;
		double tmpCRT = 0;
		
		List<Datacenter> dcs = SimManager.getInstance().edgeServerManager.getDatacenterList();
		
	    for(int i = 0; i < dcs.size(); i++) {
			double dl = SimManager.getInstance().getEdgeOrchestrator().deadline(task, SimLogger.getInstance().matrix, 0.0001,i);
								
			tmpCRT = dl - matrix.getMu(i, task.getTaskType().ordinal());
				
			if(crt < tmpCRT) {
					crt = tmpCRT;
					bestDC = i;
				}
			}
	
		return bestDC;
	}
	
	/*
	 * Fill the ArrayList for neighboring Base Stations;
	 */
	
	public void getNeighbors(Task task) {
		
		List<EdgeHost> recLoc = receivingBS.getHostList();
		Location recLocation = recLoc.get(0).getLocation();
		int xRec = recLocation.getXPos();
		int yRec = recLocation.getYPos();
		
		List<Datacenter> datacenters = SimManager.getInstance().edgeServerManager.getDatacenterList();

		for(int i = 0; i < datacenters.size(); i++) {
			List<EdgeHost> neighbourlist = datacenters.get(i).getHostList();
			Location neighLocation = neighbourlist.get(0).getLocation();
			int xNeigh = neighLocation.getXPos();
			int yNeigh = neighLocation.getYPos();
			
			//SimLogger.printLine(" neighbour bs location X :"+ xNeigh +" y :" +yNeigh);
			int xdiff = xRec-xNeigh;
			int ydiff = yRec-yNeigh;
			if(Math.abs(xdiff) == 1 || Math.abs(ydiff) == 1) {
				neighboringBS.add(i);		
			}
		}
	}

	public Datacenter getReceivingBS() {
		return receivingBS;
	}

	public static void setReceivingBS(Datacenter datacenter) {
		receivingBS = datacenter;
	}
}