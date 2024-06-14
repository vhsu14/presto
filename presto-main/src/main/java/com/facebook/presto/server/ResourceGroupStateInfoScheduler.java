package com.facebook.presto.server;

import com.facebook.presto.execution.resourceGroups.ResourceGroupManager;
import com.facebook.presto.metadata.InternalNodeManager;
import com.facebook.presto.resourcemanager.ResourceManagerProxy;
import com.facebook.presto.spi.resourceGroups.ResourceGroupId;

import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.facebook.presto.server.ResourceGroupStateInfoUtils.proxyResourceGroupInfoResponse;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

public class ResourceGroupStateInfoScheduler
{
    private final ScheduledExecutorService resourceGroupStateExecutor;
    private final ResourceGroupManager<?> resourceGroupManager;
    private final boolean resourceManagerEnabled;
    private final InternalNodeManager internalNodeManager;
    private final Optional<ResourceManagerProxy> proxyHelper;
    private Set<String> resourceGroupsIdsOnScheduler;
    private ConcurrentHashMap<String, Integer> resourceGroupStateInfoMap;
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
            for (String resourceGroupId : resourceGroupsIdsOnScheduler)
            {
                if (resourceManagerEnabled)
                {
                    proxyResourceGroupInfoResponse(proxyHelper, internalNodeManager, servletRequest, asyncResponse, xForwardedProto, uriInfo);
                } else {

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
