package com.facebook.presto.server;

import com.facebook.airlift.log.Logger;
import com.facebook.presto.execution.QueryTracker;
import com.facebook.presto.execution.resourceGroups.ResourceGroupManager;
import com.facebook.presto.metadata.InternalNodeManager;
import com.facebook.presto.resourcemanager.ResourceManagerProxy;
import com.facebook.presto.spi.resourceGroups.ResourceGroupId;
import com.google.common.base.Supplier;

import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.facebook.presto.server.ResourceGroupStateInfoUtils.getResourceGroupInfo;
import static com.facebook.presto.server.ResourceGroupStateInfoUtils.proxyResourceGroupInfoResponse;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

public class ResourceGroupStateInfoScheduler
{
    private static final Logger log = Logger.get(ResourceGroupStateInfoScheduler.class);
    private final ScheduledExecutorService resourceGroupStateExecutor;
    private final ResourceGroupManager<?> resourceGroupManager;
    private final boolean resourceManagerEnabled;
    private final InternalNodeManager internalNodeManager;
    private final Optional<ResourceManagerProxy> proxyHelper;
    private Set<String> resourceGroupsIdsOnScheduler;
    private ConcurrentHashMap<ResourceGroupId, ResourceGroupInfo> resourceGroupStateInfoMap;
    private List<ResourceGroupInfo> rootResourceGroupInfo;
    @GuardedBy("this")
    private ScheduledFuture<?> backgroundTask;

    @Inject
    public ResourceGroupStateInfoScheduler(
            ServerConfig serverConfig,
            ResourceGroupManager<?> resourceGroupManager,
            InternalNodeManager internalNodeManager,
            Optional<ResourceManagerProxy> proxyHelper,
            ScheduledExecutorService resourceGroupStateExecutor)
    {
        requireNonNull(serverConfig, "serverConfig is null");
        this.resourceManagerEnabled = serverConfig.isResourceManagerEnabled();
        this.resourceGroupsIdsOnScheduler = (serverConfig.getResourceGroupIdsOnScheduler().isPresent() ? serverConfig.getResourceGroupIdsOnScheduler().get() : Collections.emptySet());
        this.resourceGroupManager = requireNonNull(resourceGroupManager, "resourceGroupManager is null");
        this.internalNodeManager = requireNonNull(internalNodeManager, "internalNodeManager is null");
        this.proxyHelper = requireNonNull(proxyHelper, "proxyHelper is null");
        this.resourceGroupStateExecutor = requireNonNull(resourceGroupStateExecutor, "queryManagementExecutor is null");
    }

    public synchronized void start()
    {
        checkState(backgroundTask == null, "QueryTracker already started");
        backgroundTask = resourceGroupStateExecutor.scheduleWithFixedDelay(() -> {
            // Question: is this safe?
            rootResourceGroupInfo = resourceGroupManager.getRootResourceGroups();
            for (String resourceGroupIdString : resourceGroupsIdsOnScheduler)
            {
                if (resourceManagerEnabled)
                {
                    // Note this could also have exception
                    proxyResourceGroupInfoResponse(proxyHelper, internalNodeManager);
                }

                try {
                    ResourceGroupId resourceGroupId = getResourceGroupId(resourceGroupIdString);
                    // Question: do we want only this combination?
                    ResourceGroupInfo resourceGroupInfo = getResourceGroupInfo(resourceGroupManager, resourceGroupId, true, true, false);
                    resourceGroupStateInfoMap.put(resourceGroupId, resourceGroupInfo);
                }
                catch (NoSuchElementException | IllegalArgumentException e) {
                    // asyncResponse.resume(Response.status(NOT_FOUND).build());
                    log.error(e, "Error updating resourceGroupStateInfo for %s", resourceGroupIdString);
                }
            }

        }, 1, 1, TimeUnit.SECONDS);
    }

    private ResourceGroupId getResourceGroupId(String resourceGroupIdString)
    {
        return new ResourceGroupId(
                Arrays.stream(resourceGroupIdString.split("/"))
                        .map(ResourceGroupStateInfoScheduler::urlDecode)
                        .collect(toImmutableList()));
    }

    private static String urlDecode(String value)
    {
        try {
            return URLDecoder.decode(value, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new WebApplicationException(BAD_REQUEST);
        }
    }

}
