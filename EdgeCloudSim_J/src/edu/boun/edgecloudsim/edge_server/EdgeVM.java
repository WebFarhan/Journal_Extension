/*
 * Title:        EdgeCloudSim - EdgeVM
 * 
 * Description: 
 * EdgeVM adds vm type information over CloudSim's VM class
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_server;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.CloudletSchedulerDynamicWorkload;
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.ResCloudlet;
import org.cloudbus.cloudsim.Vm;

import com.sun.javafx.tk.Toolkit.Task;

import edu.boun.edgecloudsim.core.SimSettings;

public class EdgeVM extends Vm {
	private SimSettings.VM_TYPES type;
	private ArrayList<Cloudlet> queue;
	
	
	public int getQueueSize() {
		return queue.size();
	}

	public void allocateTasktoQue(edu.boun.edgecloudsim.edge_client.Task task) {
		Cloudlet c = (Cloudlet)task;
		this.queue.add(c);
	}

	private int vmChategory;//machine type cpu intensive, gpu intensive, memory intensive
	
	private CloudletSchedulerSpaceShared cloudletScheduler;

	
	public CloudletSchedulerSpaceShared getCloudletScheduler() {
		return cloudletScheduler;
	}

	public void setCloudletScheduler(CloudletSchedulerSpaceShared cloudletScheduler) {
		this.cloudletScheduler = cloudletScheduler;
	}

	public int getVmChategory() {
		return vmChategory;
	}

	public void setVmChategory(int vmChategory) {
		this.vmChategory = vmChategory;
	}

	public EdgeVM(int id, int userId, double mips, int numberOfPes, int ram,
			long bw, long size, String vmm, CloudletSchedulerSpaceShared cloudletScheduler,int vmChategory) {
		super(id, userId, mips, numberOfPes, ram, bw, size, vmm, cloudletScheduler);
		
		this.queue = new ArrayList<Cloudlet>(10);
		this.vmChategory = vmChategory;
		this.cloudletScheduler = cloudletScheduler;

	}
	
	
	
	

	public void setVmType(SimSettings.VM_TYPES _type){
		type=_type;
	}
	
	public SimSettings.VM_TYPES getVmType(){
		return type;
	}
}
