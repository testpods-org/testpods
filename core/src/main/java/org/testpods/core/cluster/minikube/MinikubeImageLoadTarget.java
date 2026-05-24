package org.testpods.core.cluster.minikube;

/** A cluster backed by a Minikube profile that can accept locally built container images. */
public interface MinikubeImageLoadTarget {

  /** Return the Minikube profile name used by {@code minikube -p <profile>}. */
  String getProfileName();
}
