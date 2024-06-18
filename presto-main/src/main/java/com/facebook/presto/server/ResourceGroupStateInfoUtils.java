package com.facebook.presto.server;

import com.facebook.airlift.http.client.Request;
import com.facebook.presto.client.QueryResults;
import com.facebook.presto.execution.resourceGroups.ResourceGroupManager;
import com.facebook.presto.metadata.InternalNode;
import com.facebook.presto.metadata.InternalNodeManager;
import com.facebook.presto.resourcemanager.ResourceManagerProxy;
import com.facebook.presto.spi.resourceGroups.ResourceGroupId;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.Iterator;
import java.util.Optional;

import static com.facebook.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static com.facebook.airlift.http.client.Request.Builder.prepareGet;
import static com.facebook.airlift.json.JsonCodec.jsonCodec;
import static com.facebook.presto.client.PrestoHeaders.PRESTO_USER;
import static com.google.common.base.Preconditions.checkState;
import static javax.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;

public class ResourceGroupStateInfoUtils
{
    private ResourceGroupStateInfoUtils() {}

    public static void proxyResourceGroupInfoResponse(Optional<ResourceManagerProxy> proxyHelper, InternalNodeManager internalNodeManager, HttpServletRequest servletRequest, AsyncResponse asyncResponse, UriInfo uriInfo)
    {
        try {
            checkState(proxyHelper.isPresent());
            Iterator<InternalNode> resourceManagers = internalNodeManager.getResourceManagers().iterator();
            if (!resourceManagers.hasNext()) {
                asyncResponse.resume(Response.status(SERVICE_UNAVAILABLE).build());
                return;
            }
            InternalNode resourceManagerNode = resourceManagers.next();

            URI uri = uriInfo.getRequestUriBuilder()
                    .scheme(resourceManagerNode.getInternalUri().getScheme())
                    .host(resourceManagerNode.getHostAndPort().toInetAddress().getHostName())
                    .port(resourceManagerNode.getInternalUri().getPort())
                    .build();
            proxyHelper.get().performRequest(servletRequest, asyncResponse, uri);
        }
        catch (Exception e) {
            asyncResponse.resume(e);
        }
    }

    public static ResourceGroupInfo getResourceGroupInfo(ResourceGroupManager<?> resourceGroupManager, ResourceGroupId resourceGroupId, boolean includeQueryInfo, boolean summarizeSubgroups, boolean includeStaticSubgroupsOnly)
    {
        return resourceGroupManager.getResourceGroupInfo(
                resourceGroupId,
                includeQueryInfo,
                summarizeSubgroups,
                includeStaticSubgroupsOnly);
    }

    public static void directResourceGroupInfoResponse(Optional<ResourceManagerProxy> proxyHelper, InternalNodeManager internalNodeManager)
    {
        try {
            checkState(proxyHelper.isPresent());
            Iterator<InternalNode> resourceManagers = internalNodeManager.getResourceManagers().iterator();
            if (!resourceManagers.hasNext()) {
//                asyncResponse.resume(Response.status(SERVICE_UNAVAILABLE).build());
                return;
            }
            InternalNode resourceManagerNode = resourceManagers.next();

            Request request = prepareGet()
                    .setUri(resourceManagerNode.getInternalUri())
                    .build();
//                    .setHeader(PRESTO_USER, user)
//                    .setUri(queryResults.getNextUri())
//                    .build();
//
//            queryResults = client.execute(request, createJsonResponseHandler(jsonCodec(QueryResults.class)));

//            URI uri = uriInfo.getRequestUriBuilder()
//                    .scheme(resourceManagerNode.getInternalUri().getScheme())
//                    .host(resourceManagerNode.getHostAndPort().toInetAddress().getHostName())
//                    .port(resourceManagerNode.getInternalUri().getPort())
//                    .build();
//            proxyHelper.get().performRequest(servletRequest, asyncResponse, uri);
        }
        catch (Exception e) {
//            asyncResponse.resume(e);
        }
    }
}
