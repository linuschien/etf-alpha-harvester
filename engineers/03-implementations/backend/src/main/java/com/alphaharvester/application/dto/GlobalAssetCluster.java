package com.alphaharvester.application.dto;

import com.alphaharvester.domain.entity.GlobalAssetScore;

import java.util.List;

/**
 * Objective data container for a single asset cluster under Leader-Follower Star Topology.
 * Contains the cluster ID, the leader asset score, any homogeneous alternative candidates,
 * and a boolean indicating whether this cluster represents a singleton (unique track).
 */
public record GlobalAssetCluster(
        Integer clusterId,
        GlobalAssetScore leader,
        List<ClusterAlternative> alternatives,
        Boolean isSingleton
) {}

