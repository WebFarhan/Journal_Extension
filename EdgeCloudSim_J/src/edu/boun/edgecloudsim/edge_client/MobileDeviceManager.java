/*
 * Title:        EdgeCloudSim - Mobile Device Manager
 * 
 * Description: 
 * MobileDeviceManager is responsible for submitting the tasks to the related
 * device by using the Edge Orchestrator. It also takes proper actions 
 * when the execution of the tasks are finished.
 * By default, MobileDeviceManager sends tasks to the edge servers or
 * cloud servers. If you want to use different topology, for example
 * MAN edge server, you should modify the flow defined in this class.
 * 
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.lists.VmList;

import com.google.common.collect.ArrayListMultimap;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_orchestrator.BasicEdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.network.NetworkModel;
import edu.boun.edgecloudsim.utils.EdgeTask;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.NormDistr;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;


public class MobileDeviceManager extends DatacenterBroker {
	private static final int BASE = 100000; //start from base in order not to conflict cloudsim tag!
	private static final int REQUEST_RECEIVED_BY_CLOUD = BASE + 1;
	private static final int REQUEST_PROCESSED_BY_CLOUD = BASE + 2;
	private static final int REQUEST_RECIVED_BY_EDGE_DEVICE = BASE + 3;
	private static final int RESPONSE_RECEIVED_BY_MOBILE_DEVICE = BASE + 4;
	private static final int REQUEST_RECIVED_BY_EDGE_ORCHESTRATOR = BASE + 5;
	private ArrayListMultimap<String, Double> executionTimes1 = ArrayListMultimap.create();
	private ArrayListMultimap<String, Double> executionTimes2 = ArrayListMultimap.create();
	private HashMap<String, NormDistr> distributions = new HashMap<>();
	private int taskIdCounter=0;
	
	private static final List<Task> taskBatchList = new ArrayList<Task>();
	
	private int dlMisCounter = 0 ;
	private int counter = 0;

	public MobileDeviceManager() throws Exception {
		super("Global_Broker");
	}

	/**
	 * Submit cloudlets to the created VMs.
	 * 
	 * @pre $none
	 * @post $none
	 */
	@Override
	protected void submitCloudlets() {
		Log.printLine(" I am in override function");
		int vmIndex = 0;
		List <Cloudlet> sortList= new ArrayList<Cloudlet>();
		
		ArrayList<Cloudlet> tempList = new ArrayList<Cloudlet>();
		
		for(Cloudlet cloudlet: getCloudletList())
		{
			tempList.add(cloudlet);
		}
		
		int totalCloudlets= tempList.size();
		
		
		for(int i=0;i<totalCloudlets;i++)
		{
	
			Cloudlet smallestCloudlet= tempList.get(0);
			for(Cloudlet checkCloudlet: tempList)
			{
				if(smallestCloudlet.getCloudletLength()>checkCloudlet.getCloudletLength())
				{
					smallestCloudlet= checkCloudlet;
					}
				}
				sortList.add(smallestCloudlet);
				tempList.remove(smallestCloudlet);
				
		}
		
		int count=1;
		
		for(Cloudlet printCloudlet: sortList)
		{
			Log.printLine(count+".Cloudlet Id:"+printCloudlet.getCloudletId()+",Cloudlet Length:"+printCloudlet.getCloudletLength());
		    count++;
		}
		
		
		for (Cloudlet cloudlet : sortList) {
			Vm vm;
			// if user didn't bind this cloudlet and it has not been executed yet
			if (cloudlet.getVmId() == -1) {
				vm = getVmsCreatedList().get(vmIndex);
			} else { // submit to the specific vm
				vm = VmList.getById(getVmsCreatedList(), cloudlet.getVmId());
				if (vm == null) { // vm was not created
					Log.printLine(CloudSim.clock() + ": " + getName() + ": Postponing execution of cloudlet "
							+ cloudlet.getCloudletId() + ": bount VM not available");
					continue;
				}
			}

			Log.printLine(CloudSim.clock() + ": " + getName() + ": Sending cloudlet "
					+ cloudlet.getCloudletId() + " to VM #" + vm.getId());
			cloudlet.setVmId(vm.getId());
			sendNow(getVmsToDatacentersMap().get(vm.getId()), CloudSimTags.CLOUDLET_SUBMIT, cloudlet);
			cloudletsSubmitted++;
			vmIndex = (vmIndex + 1) % getVmsCreatedList().size();
			getCloudletSubmittedList().add(cloudlet);
		}

		// remove submitted cloudlets from waiting list
		for (Cloudlet cloudlet : getCloudletSubmittedList()) {
			getCloudletList().remove(cloudlet);
		}
		
	}
	
	/**
	 * Process a cloudlet return event.
	 * 
	 * @param ev a SimEvent object
	 * @pre ev != $null
	 * @post $none
	 */
	protected void processCloudletReturn(SimEvent ev) {
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		Task task = (Task) ev.getData();

		Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock());
		//if(task.getSubmittedLocation().equals(currentLocation))
		//{
			//SimLogger.printLine(CloudSim.clock() + ": " + getName() + ": Cloudlet " + task.getCloudletId() + " received");
			double WlanDelay = networkModel.getDownloadDelay(task.getAssociatedHostId(), task.getMobileDeviceId(), task.getCloudletOutputSize());
			if(WlanDelay > 0)
			{
				networkModel.downloadStarted(currentLocation, SimSettings.GENERIC_EDGE_DEVICE_ID);
				schedule(getId(), WlanDelay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
				SimLogger.getInstance().downloadStarted(task.getCloudletId(), WlanDelay);
			}
			else
			{
				SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock());
			}
		}
		//else
		//{
			//SimLogger.printLine("task cannot be finished due to mobility of user!");
			//SimLogger.printLine("device: " +task.getMobileDeviceId()+" - submitted " + task.getSubmissionTime() + " @ " + task.getSubmittedLocation().getXPos() + " handled " + CloudSim.clock() + " @ " + currentLocation.getXPos());
		//	SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
		//}
	//}
	
	int amx = 0;
	protected void processOtherEvent(SimEvent ev) {
		
		if (ev == null) {
			SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - an event is null! Terminating simulation...");
			System.exit(0);
			return;
		}
		
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
		
		switch (ev.getTag()) {
			case REQUEST_RECEIVED_BY_CLOUD:
			{
				Task task = (Task) ev.getData();
				
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);
				
				//save related host id
				task.setAssociatedHostId(SimSettings.CLOUD_HOST_ID);
				
				SimLogger.getInstance().uploaded(task.getCloudletId(),
						SimSettings.CLOUD_DATACENTER_ID,
						SimSettings.CLOUD_HOST_ID,
						SimSettings.CLOUD_VM_ID,
						SimSettings.VM_TYPES.CLOUD_VM.ordinal(),
						CloudSim.clock()
						);
				
								
				//calculate computational delay in cloud
				double ComputationDelay = (double)task.getCloudletLength() / (double)SimSettings.getInstance().getMipsForCloud();
				
				schedule(getId(), ComputationDelay, REQUEST_PROCESSED_BY_CLOUD, task);
				
				break;
			}
			case REQUEST_PROCESSED_BY_CLOUD:
			{
				Task task = (Task) ev.getData();

				//SimLogger.printLine(CloudSim.clock() + ": " + getName() + ": Cloudlet " + task.getCloudletId() + " received");
				double WanDelay = networkModel.getDownloadDelay(SimSettings.CLOUD_DATACENTER_ID, task.getMobileDeviceId(), task.getCloudletOutputSize());
				if(WanDelay > 0)
				{
					Location currentLocation = SimManager.getInstance().getMobilityModel().getLocation(task.getMobileDeviceId(),CloudSim.clock()+WanDelay);
					if(task.getSubmittedLocation().equals(currentLocation))
					{
						networkModel.downloadStarted(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);
						schedule(getId(), WanDelay, RESPONSE_RECEIVED_BY_MOBILE_DEVICE, task);
						SimLogger.getInstance().downloadStarted(task.getCloudletId(), WanDelay);
					}
					else
					{
						//SimLogger.printLine("task cannot be finished due to mobility of user!");
						//SimLogger.printLine("device: " +task.getMobileDeviceId()+" - submitted " + task.getSubmissionTime() + " @ " + task.getSubmittedLocation().getXPos() + " handled " + CloudSim.clock() + " @ " + currentLocation.getXPos());
						SimLogger.getInstance().failedDueToMobility(task.getCloudletId(), CloudSim.clock());
					}
				}
				else
				{
					SimLogger.getInstance().failedDueToBandwidth(task.getCloudletId(), CloudSim.clock());
				}
				break;
			}
			
			case REQUEST_RECIVED_BY_EDGE_ORCHESTRATOR:
			{
				Task task = (Task) ev.getData();
				double internalDelay = networkModel.getDownloadDelay(
						SimSettings.EDGE_ORCHESTRATOR_ID,
						SimSettings.GENERIC_EDGE_DEVICE_ID,
						task.getCloudletOutputSize());
						
				networkModel.downloadStarted(task.getSubmittedLocation(), task.getMobileDeviceId());
				
				//if(BasicEdgeOrchestrator.getBasicEdgePlociy() == "EdgeCloud") {
				//	edgeCloudSimDefaultScheduling(task,(internalDelay+amx++));
					
				//}else {
				//	submitTaskToEdgeDevice(task,(internalDelay+amx++));
				//}
				
				submitTaskToEdgeDevice(task,(internalDelay+amx++));
				
				break;
			}
			
			case REQUEST_RECIVED_BY_EDGE_DEVICE:
			{
				Task task = (Task) ev.getData();
				
				networkModel.uploadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
				
				submitTaskToEdgeDevice(task,0);
				//edgeCloudSimDefaultScheduling(task,0);
				
				break;
			}
			case RESPONSE_RECEIVED_BY_MOBILE_DEVICE:
			{
				Task task = (Task) ev.getData();
				
				
				if(task.getAssociatedHostId() == SimSettings.CLOUD_HOST_ID)
					networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.CLOUD_DATACENTER_ID);
				else
					networkModel.downloadFinished(task.getSubmittedLocation(), SimSettings.GENERIC_EDGE_DEVICE_ID);
				
				
				//SimLogger.printLine(" task# "+task.getCloudletId()+" submission time "+ task.getSubmissionTime());
				//SimLogger.printLine(" task# "+task.getCloudletId()+" finish time "+ task.getFinishTime());
				
				if(task.getFinishTime()>(task.getDeadLine()+task.getSubmissionTime())) {
					
					SimLogger.getInstance().setDlMisCounter(dlMisCounter++);
			
				}
								
				SimLogger.getInstance().downloaded(task.getCloudletId(), CloudSim.clock());
				break;
			}
			default:
				SimLogger.printLine(getName() + ".processOtherEvent(): " + "Error - event unknown by this DatacenterBroker. Terminating simulation...");
				System.exit(0);
				break;
		}
	}
	
	//function created for scheduling
	public void taskSchedulingSJF() {
		
		SimLogger.printLine(" I am in SJF function");
		int vmIndex = 0;
		List <Cloudlet> sortList= new ArrayList<Cloudlet>();
		
		ArrayList<Cloudlet> tempList = new ArrayList<Cloudlet>();
		
		for(Cloudlet cloudlet: getCloudletList())
		{
			tempList.add(cloudlet);
		}
		
		int totalCloudlets= tempList.size();
		
		
		//sorting the tasks from smaller to larger
		for(int i=0;i<totalCloudlets;i++)
		{
	
			Cloudlet smallestCloudlet= tempList.get(0);
			for(Cloudlet checkCloudlet: tempList)
			{
				if(smallestCloudlet.getCloudletLength()>checkCloudlet.getCloudletLength())
				{
					smallestCloudlet= checkCloudlet;
					}
				}
				sortList.add(smallestCloudlet);
				tempList.remove(smallestCloudlet);
				
		}
		
		int count=1;
		
		for(Cloudlet printCloudlet: sortList)
		{
			SimLogger.printLine(count+".Cloudlet Id:"+printCloudlet.getCloudletId()+",Cloudlet Length:"+printCloudlet.getCloudletLength());
		    count++;
		}
		
		//sortList is the sorted list
		for (Cloudlet cloudlet : sortList) {
			Vm vm;
			Task task = (Task)cloudlet;
			
			
			if (cloudlet.getVmId() == -1) {
				vm = getVmsCreatedList().get(vmIndex);
			} else { // submit to the specific vm
				vm = SimManager.getInstance().getEdgeOrchestrator().getVmToOffload(task);
				if (vm == null) { // vm was not created
					Log.printLine(CloudSim.clock() + ": " + getName() + ": Postponing execution of cloudlet "
							+ cloudlet.getCloudletId() + ": bount VM not available");
					continue;
				}
			}

			
			
			EdgeVM selectedVM = SimManager.getInstance().getEdgeOrchestrator().getVmToOffload(task);
			bindCloudletToVm(cloudlet.getCloudletId(),selectedVM.getId());
			schedule(getVmsToDatacentersMap().get(task.getVmId()), task.getTaskDelay(), CloudSimTags.CLOUDLET_SUBMIT, task);
		    
			cloudletsSubmitted++;
			vmIndex = (vmIndex + 1) % getVmsCreatedList().size();
			getCloudletSubmittedList().add(task);
			
			
			SimLogger.getInstance().uploaded(task.getCloudletId(),
					selectedVM.getHost().getDatacenter().getId(),
					selectedVM.getHost().getId(),
					selectedVM.getId(),
					selectedVM.getVmType().ordinal(),
					CloudSim.clock());
		
		}

		// remove submitted cloudlets from waiting list
		for (Cloudlet cloudlet : getCloudletSubmittedList()) {
			getCloudletList().remove(cloudlet);
		}
		
	
	
	}
	
	
	public void taskSchedulingFCFS() {
		int vmIndex = 0;
		
		SimLogger.printLine(" I am in FCFS function");
		int count=1;
		for(Cloudlet printCloudlet: getCloudletList())
		{
			SimLogger.printLine(count+".Cloudlet Id:"+printCloudlet.getCloudletId()+",Cloudlet Length:"+printCloudlet.getCloudletLength());
		    count++;
		}
		
		for (Cloudlet cloudlet : getCloudletList()) {
			Vm vm;
			Task task = (Task)cloudlet;
			if (cloudlet.getVmId() == -1) {
			vm = getVmsCreatedList().get(vmIndex);
			} else { // submit to the specific vm
				vm = SimManager.getInstance().getEdgeOrchestrator().getVmToOffload(task);
				if (vm == null) { // vm was not created
					SimLogger.printLine(CloudSim.clock() + ": " + getName() + ": Postponing execution of cloudlet "
							+ cloudlet.getCloudletId() + ": bount VM not available");
					continue;
				}
			}

			EdgeVM selectedVM = SimManager.getInstance().getEdgeOrchestrator().getVmToOffload(task);
			if(selectedVM != null) {
				bindCloudletToVm(cloudlet.getCloudletId(),selectedVM.getId());
				schedule(getVmsToDatacentersMap().get(task.getVmId()), task.getTaskDelay(), CloudSimTags.CLOUDLET_SUBMIT, task);
				    
				cloudletsSubmitted++;
				vmIndex = (vmIndex + 1) % getVmsCreatedList().size();
				getCloudletSubmittedList().add(task);
					
					
				SimLogger.getInstance().uploaded(task.getCloudletId(),
						selectedVM.getHost().getDatacenter().getId(),
						selectedVM.getHost().getId(),
						selectedVM.getId(),
						selectedVM.getVmType().ordinal(),
						CloudSim.clock());
			}
			else {
				int tsize = SimManager.getInstance().getEdgeOrchestrator().getTempListSize();
				Task t = SimManager.getInstance().getEdgeOrchestrator().getElementTempList(0);
			    int svm = SimUtils.getRandomNumber(0, 8);
								
				SimLogger.getInstance().rejectedDueToVMCapacity(task.getCloudletId(), CloudSim.clock(), task.getDc());
				
			   }
			}

			// remove submitted cloudlets from waiting list
		for (Cloudlet cloudlet : getCloudletSubmittedList()) {
			getCloudletList().remove(cloudlet);
		}
			
	}
	
	
	public void edgeCloudSimDefaultScheduling(Task task, double delay) {
		
		EdgeVM selectedVM = SimManager.getInstance().getEdgeOrchestrator().getVmToOffload(task);
		//task.setTaskDelay(delay);
		
		if(selectedVM != null){
			//save related host id
			task.setAssociatedHostId(selectedVM.getHost().getId());

		    //bind task to related VM
			getCloudletList().add(task);
			//SimLogger.printLine(" Cloudlet list length "+getCloudletList().size());			
			
			bindCloudletToVm(task.getCloudletId(),selectedVM.getId());
			
			schedule(getVmsToDatacentersMap().get(task.getVmId()), delay, CloudSimTags.CLOUDLET_SUBMIT, task);
			
	        SimLogger.getInstance().uploaded(task.getCloudletId(),
					selectedVM.getHost().getDatacenter().getId(),
					selectedVM.getHost().getId(),
					selectedVM.getId(),
					selectedVM.getVmType().ordinal(),
					CloudSim.clock());
		}
		else{
			SimLogger.getInstance().rejectedDueToVMCapacity(task.getCloudletId(), CloudSim.clock(), task.getDc());
			
		}
		
	}
	
	
	public void submitTaskToEdgeDevice(Task task, double delay) {
		//select a VM
		EdgeVM selectedVM = SimManager.getInstance().getEdgeOrchestrator().getVmToOffload(task);
						
		task.setTaskDelay(delay);
		
		if(selectedVM != null){
			//save related host id
			task.setAssociatedHostId(selectedVM.getHost().getId());

		    //bind task to related VM
			getCloudletList().add(task);
			//SimLogger.printLine(" Cloudlet list length "+getCloudletList().size());			
			
			//bindCloudletToVm(task.getCloudletId(),selectedVM.getId());
			
			//schedule(getVmsToDatacentersMap().get(task.getVmId()), delay, CloudSimTags.CLOUDLET_SUBMIT, task);
			
	        //SimLogger.getInstance().uploaded(task.getCloudletId(),
			//		selectedVM.getHost().getDatacenter().getId(),
			//		selectedVM.getHost().getId(),
			//		selectedVM.getId(),
			//		selectedVM.getVmType().ordinal(),
			//		CloudSim.clock());
		}
		else{
			SimLogger.getInstance().rejectedDueToVMCapacity(task.getCloudletId(), CloudSim.clock(), task.getDc());
			
		}
		
	}
	
	public void submitTask(EdgeTask edgeTask) {
		NetworkModel networkModel = SimManager.getInstance().getNetworkModel();
				
		//create a task
		Task task = createTask(edgeTask);
		
		Location currentLocation = SimManager.getInstance().getMobilityModel().
				getLocation(task.getMobileDeviceId(),CloudSim.clock());
		
		//set location of the mobile device which generates this task
		task.setSubmittedLocation(currentLocation);


		int nextHopId = SimManager.getInstance().getEdgeOrchestrator().getDeviceToOffload(task);// getDeviceToOffload is in BasicEdgeOrchestration
		
			
		SimLogger.getInstance().addLog(CloudSim.clock(),
				task.getCloudletId(),
				task.getTaskType().ordinal(),
				(int)task.getCloudletLength(),
				(int)task.getCloudletFileSize(),
				(int)task.getCloudletOutputSize(), nextHopId, task.getVmId());
		
		if(nextHopId == SimSettings.CLOUD_DATACENTER_ID){
			double WanDelay = networkModel.getUploadDelay(task.getMobileDeviceId(), nextHopId, task.getCloudletFileSize());
			
			if(WanDelay>0){
				networkModel.uploadStarted(currentLocation, nextHopId);
				schedule(getId(), WanDelay, REQUEST_RECEIVED_BY_CLOUD, task);
				SimLogger.getInstance().uploadStarted(task.getCloudletId(),WanDelay);
			}
			else
			{
				//SimLogger.printLine("Task #" + task.getCloudletId() + " cannot assign to any VM");
				SimLogger.getInstance().rejectedDueToBandwidth(
						task.getCloudletId(),
						CloudSim.clock(),
						SimSettings.VM_TYPES.CLOUD_VM.ordinal());
			}
		}
		else if(nextHopId == SimSettings.EDGE_ORCHESTRATOR_ID){
			
			double WlanDelay = networkModel.getUploadDelay(task.getMobileDeviceId(), nextHopId, task.getCloudletFileSize());
			
			if(WlanDelay > 0){
				networkModel.uploadStarted(currentLocation, nextHopId);
				schedule(getId(), WlanDelay, REQUEST_RECIVED_BY_EDGE_ORCHESTRATOR, task);
				SimLogger.getInstance().uploadStarted(task.getCloudletId(),WlanDelay);
			}
			else {
				SimLogger.getInstance().rejectedDueToBandwidth(
						task.getCloudletId(),
						CloudSim.clock(),
						SimSettings.VM_TYPES.EDGE_VM.ordinal());
			}
		}
		else if(nextHopId == SimSettings.GENERIC_EDGE_DEVICE_ID) { // simulation generate this id.
			
			//SimLogger.printLine( " Deadline of task "+task.getCloudletId()+ " is : "+ task.getDeadLine() + " submission time "+task.getSubmissionTime());
			
			double WlanDelay = networkModel.getUploadDelay(task.getMobileDeviceId(), nextHopId, task.getCloudletFileSize());
			
			if(WlanDelay > 0){
				networkModel.uploadStarted(currentLocation, nextHopId);
				schedule(getId(), WlanDelay, REQUEST_RECIVED_BY_EDGE_DEVICE, task);
				SimLogger.getInstance().uploadStarted(task.getCloudletId(),WlanDelay);
			}
			else {
				SimLogger.getInstance().rejectedDueToBandwidth(
						task.getCloudletId(),
						CloudSim.clock(),
						SimSettings.VM_TYPES.EDGE_VM.ordinal());
			}
		}
		else {
			SimLogger.printLine("Unknown nextHopId! Terminating simulation...");
			System.exit(0);
		}
		
		//add related task to the logger 
		
		
	}
	
	public Task createTask(EdgeTask edgeTask){
		UtilizationModel utilizationModel = new UtilizationModelFull(); /*UtilizationModelStochastic*/
		UtilizationModel utilizationModelCPU = SimManager.getInstance().getScenarioFactory().getCpuUtilizationModel(edgeTask.taskType);
        
		int urgntF = 0;
		
		if(edgeTask.length >=4000) {
			urgntF = 0;
		}
		else {
			urgntF = 1;
		}
		
		int taskPref = SimUtils.getRandomNumber(0, 2);
		
		Task task = new Task(edgeTask.mobileDeviceId,urgntF ,0.0 ,++taskIdCounter,
							edgeTask.length, edgeTask.pesNumber,
							edgeTask.inputFileSize, edgeTask.outputFileSize,
							utilizationModelCPU, utilizationModel, utilizationModel,taskPref,0);
		
		//set the owner of this task
		task.setUserId(this.getId());
		task.setTaskType(edgeTask.taskType);
		
		return task;
	}

	public void taskEnded(){
		clearDatacenters();
		finishExecution();
	}
}
