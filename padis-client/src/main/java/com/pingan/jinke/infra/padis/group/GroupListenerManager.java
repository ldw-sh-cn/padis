package com.pingan.jinke.infra.padis.group;

import java.util.List;
import java.util.Set;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent.Type;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Sets;
import com.pingan.jinke.infra.padis.common.AbstractListenerManager;
import com.pingan.jinke.infra.padis.common.ClusterManager;
import com.pingan.jinke.infra.padis.common.CoordinatorRegistryCenter;
import com.pingan.jinke.infra.padis.common.HostAndPort;
import com.pingan.jinke.infra.padis.core.AbstractClientPoolManager;
import com.pingan.jinke.infra.padis.node.Group;
import com.pingan.jinke.infra.padis.storage.AbstractNodeListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GroupListenerManager extends AbstractListenerManager {

	private GroupService groupService;

	public GroupListenerManager(String instance, CoordinatorRegistryCenter coordinatorRegistryCenter,
			ClusterManager clusterManager) {
		super(instance, coordinatorRegistryCenter, clusterManager);
		this.groupService = new GroupService(coordinatorRegistryCenter);
	}

	public List<Group> getAllGroups() {
		return this.groupService.getAllGroups();
	}

	@Override
	public void start() {
		addDataListener(new GroupStatusListener(), groupService.getRootGroupPath());
	}

	public AbstractClientPoolManager getClusterManager(){
    	return (AbstractClientPoolManager) clusterManager;
    }
	
	class GroupStatusListener extends AbstractNodeListener {

		@Override
		protected void dataChanged(CuratorFramework client, TreeCacheEvent event, String path) {
			try{
				String json = new String(event.getData().getData());
				
				if (Type.NODE_UPDATED == event.getType()) {
					Group group = JSON.parseObject(json, Group.class);
					getClusterManager().addGroup(group);
					Group oldGroup = getClusterManager().getGroup(group.getId());
	
					Set<HostAndPort> set = Sets.newHashSet();
					set.add(group.getMaster());
					set.add(group.getSlave());
	
					if (!set.contains(oldGroup.getMaster())) {
						getClusterManager().closePool(oldGroup.getMaster());
					}
	
					if (!set.contains(oldGroup.getSlave())) {
						getClusterManager().closePool(oldGroup.getSlave());
					}
					
				} else if (Type.NODE_ADDED == event.getType()&&!json.isEmpty()) {
					Group group = JSON.parseObject(json, Group.class);
					getClusterManager().addGroup(group);
				} else if (Type.NODE_REMOVED == event.getType()) {
					Group group = JSON.parseObject(json, Group.class);
					getClusterManager().closePool(group.getMaster());
					getClusterManager().closePool(group.getSlave());
				}
			}catch(Throwable t){
				log.error("update group fail!", t);
			}
		}

	}
}
