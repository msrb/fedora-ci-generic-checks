FROM fedora:latest
LABEL maintainer "https://pagure.io/fedora-ci-generic-checks"
LABEL description="This container is meant to \
run generic checks against koji builds."


# Set the labels that are used for OpenShift to describe the builder image.
LABEL io.k8s.description="Fedora generic-check-worker" \
    io.k8s.display-name="Fedora:latest generic-check-worker" \
    io.openshift.tags="builder,fedora-ci" \
    # this label tells s2i where to find its mandatory scripts
    # (run, assemble, save-artifacts)
    io.openshift.s2i.scripts-url="image:///usr/libexec/s2i"

# we need dnf-plugins-core to interface with copr
RUN dnf -y install dnf-plugins-core

# enable the rpminspect copr
RUN dnf -y copr enable dcantrel/rpminspect

# Install all package requirements
RUN dnf -y install ansible \
        curl \
        file \
        findutils \
        git \
        libselinux-python \
        python3-dnf \
        python3-devel \
        rpm-build \
        rsync \
        sudo \
        wget \
        rpminspect \
        rpmdeplint \
        rpmlint \
        && dnf clean all


# create the workdir for jenkins
RUN mkdir /workDir
RUN chmod -R 777 /workDir

# Copy the S2I scripts to /usr/libexec/s2i since we set the label that way
#COPY ./s2i/bin/ /usr/libexec/s2i

# Copy the build scripts to the container
COPY run-rpminspect.sh /tmp/

# Run the build script
# assumes that target ENVR is available as $TARGET_ENVR
ENTRYPOINT ["bash", "/tmp/run-rpminspect.sh"]
#CMD ["/usr/libexec/s2i/usage"]
