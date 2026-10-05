package com.alphaharvester.application.dto;

import com.alphaharvester.domain.entity.GlobalAssetScore;

/**
 * Objective data container for a homogeneous alternative candidate in a cluster.
 * Contains the candidate's full score details and the coefficient of determination (R^2)
 * relative to the cluster leader.
 */
public record ClusterAlternative(
        GlobalAssetScore score,
        Double rSquared
) {}

