# -*- mode: ruby -*-
# vi: set ft=ruby :
#
# SmartLingua — Vagrant DevOps Environment
# =========================================
# Provisions a single Ubuntu 24.04 VM with:
#   - Docker + Docker Compose v2
#   - kubectl + kubeadm + kubelet (Kubernetes)
#   - Helm 3
#   - Java 21 (Temurin)
#   - Node.js 20 LTS + npm
#   - Maven 3.9
#   - SonarScanner CLI
#   - Git, curl, wget, unzip
#
# Usage:
#   vagrant up            → provision full environment (first time: ~10 min)
#   vagrant ssh           → enter the VM
#   vagrant halt          → graceful shutdown
#   vagrant destroy -f    → destroy the VM completely
#   vagrant reload        → restart the VM

Vagrant.configure("2") do |config|

  # ── Base box ────────────────────────────────────────────────────────────────
  config.vm.box = "ubuntu/jammy64"   # Ubuntu 22.04 LTS (Jammy)
  config.vm.box_check_update = false

  # ── VM name ─────────────────────────────────────────────────────────────────
  config.vm.define "smartlingua-devops" do |node|
    node.vm.hostname = "smartlingua-devops"
  end

  # ── Network ──────────────────────────────────────────────────────────────────
  # Private network so the host can access all services
  config.vm.network "private_network", ip: "192.168.56.10"

  # Forward key service ports to the host machine
  config.vm.network "forwarded_port", guest: 4200,  host: 4200,  host_ip: "127.0.0.1"   # Frontend
  config.vm.network "forwarded_port", guest: 8093,  host: 8093,  host_ip: "127.0.0.1"   # API Gateway
  config.vm.network "forwarded_port", guest: 8761,  host: 8761,  host_ip: "127.0.0.1"   # Eureka
  config.vm.network "forwarded_port", guest: 8890,  host: 8890,  host_ip: "127.0.0.1"   # Config Server
  config.vm.network "forwarded_port", guest: 8081,  host: 8081,  host_ip: "127.0.0.1"   # Keycloak
  config.vm.network "forwarded_port", guest: 9090,  host: 9090,  host_ip: "127.0.0.1"   # Prometheus
  config.vm.network "forwarded_port", guest: 3000,  host: 3000,  host_ip: "127.0.0.1"   # Grafana
  config.vm.network "forwarded_port", guest: 3306,  host: 3306,  host_ip: "127.0.0.1"   # MySQL
  config.vm.network "forwarded_port", guest: 6443,  host: 6443,  host_ip: "127.0.0.1"   # K8s API server
  # Microservices (direct access for debugging / actuator)
  config.vm.network "forwarded_port", guest: 8087,  host: 8087,  host_ip: "127.0.0.1"   # users
  config.vm.network "forwarded_port", guest: 8086,  host: 8086,  host_ip: "127.0.0.1"   # courses
  config.vm.network "forwarded_port", guest: 8088,  host: 8088,  host_ip: "127.0.0.1"   # quiz
  config.vm.network "forwarded_port", guest: 8089,  host: 8089,  host_ip: "127.0.0.1"   # exams
  config.vm.network "forwarded_port", guest: 8096,  host: 8096,  host_ip: "127.0.0.1"   # forum
  config.vm.network "forwarded_port", guest: 8092,  host: 8092,  host_ip: "127.0.0.1"   # messaging
  config.vm.network "forwarded_port", guest: 8091,  host: 8091,  host_ip: "127.0.0.1"   # privetcours
  config.vm.network "forwarded_port", guest: 8094,  host: 8094,  host_ip: "127.0.0.1"   # adaptive-learning
  config.vm.network "forwarded_port", guest: 8095,  host: 8095,  host_ip: "127.0.0.1"   # ai-assistant

  # ── Resources ────────────────────────────────────────────────────────────────
  config.vm.provider "virtualbox" do |vb|
    vb.name   = "SmartLingua-DevOps"
    vb.memory = "6144"    # 6 GB RAM (increase if possible)
    vb.cpus   = 4
    vb.customize ["modifyvm", :id, "--natdnshostresolver1", "on"]
    vb.customize ["modifyvm", :id, "--ioapic", "on"]
  end

  # ── Synced folder: share project into the VM ─────────────────────────────────
  config.vm.synced_folder ".", "/home/vagrant/smartlingua",
    owner: "vagrant", group: "vagrant",
    mount_options: ["dmode=775,fmode=664"]

  # ── Provisioning ─────────────────────────────────────────────────────────────
  config.vm.provision "shell", privileged: true, name: "system-setup", inline: <<-SHELL
    set -euo pipefail

    echo "=================================================="
    echo " SmartLingua DevOps Environment Setup"
    echo "=================================================="

    export DEBIAN_FRONTEND=noninteractive

    # ── 1. System update ────────────────────────────────────────────────────────
    apt-get update -qq
    apt-get install -y -qq \
      curl wget git unzip gnupg2 lsb-release \
      ca-certificates apt-transport-https software-properties-common \
      build-essential net-tools jq

    # ── 2. Docker Engine ────────────────────────────────────────────────────────
    echo "--- Installing Docker..."
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
      https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
      > /etc/apt/sources.list.d/docker.list
    apt-get update -qq
    apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    usermod -aG docker vagrant
    systemctl enable docker
    systemctl start docker

    # ── 3. Java 21 (Eclipse Temurin) ────────────────────────────────────────────
    echo "--- Installing Java 21..."
    wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
    echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" \
      > /etc/apt/sources.list.d/adoptium.list
    apt-get update -qq
    apt-get install -y -qq temurin-21-jdk
    echo "JAVA_HOME=/usr/lib/jvm/temurin-21" >> /etc/environment
    echo "PATH=\$PATH:/usr/lib/jvm/temurin-21/bin" >> /etc/environment

    # ── 4. Maven 3.9 ────────────────────────────────────────────────────────────
    echo "--- Installing Maven 3.9..."
    MAVEN_VERSION=3.9.9
    wget -qO /tmp/maven.tar.gz "https://downloads.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
    tar -xzf /tmp/maven.tar.gz -C /opt
    ln -sf /opt/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/local/bin/mvn
    rm /tmp/maven.tar.gz

    # ── 5. Node.js 20 LTS ───────────────────────────────────────────────────────
    echo "--- Installing Node.js 20..."
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
    apt-get install -y -qq nodejs

    # ── 6. kubectl ──────────────────────────────────────────────────────────────
    echo "--- Installing kubectl..."
    KUBECTL_VERSION=$(curl -sL https://dl.k8s.io/release/stable.txt)
    curl -sLO "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl"
    chmod +x kubectl
    mv kubectl /usr/local/bin/kubectl

    # ── 7. kubeadm + kubelet ────────────────────────────────────────────────────
    echo "--- Installing kubeadm + kubelet..."
    curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.30/deb/Release.key | gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg
    echo "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.30/deb/ /" \
      > /etc/apt/sources.list.d/kubernetes.list
    apt-get update -qq
    apt-get install -y -qq kubelet kubeadm
    apt-mark hold kubelet kubeadm kubectl

    # ── 8. Helm 3 ───────────────────────────────────────────────────────────────
    echo "--- Installing Helm 3..."
    curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

    # ── 9. SonarScanner CLI ─────────────────────────────────────────────────────
    echo "--- Installing SonarScanner CLI..."
    SONAR_VERSION=6.2.1.4610
    wget -qO /tmp/sonar-scanner.zip "https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-${SONAR_VERSION}-linux-x64.zip"
    unzip -q /tmp/sonar-scanner.zip -d /opt
    ln -sf /opt/sonar-scanner-${SONAR_VERSION}-linux-x64/bin/sonar-scanner /usr/local/bin/sonar-scanner
    rm /tmp/sonar-scanner.zip

    # ── 10. Verify installations ────────────────────────────────────────────────
    echo ""
    echo "=================================================="
    echo " Verification"
    echo "=================================================="
    echo "Docker:         $(docker --version)"
    echo "Docker Compose: $(docker compose version)"
    echo "Java:           $(java -version 2>&1 | head -1)"
    echo "Maven:          $(mvn -version 2>&1 | head -1)"
    echo "Node.js:        $(node --version)"
    echo "npm:            $(npm --version)"
    echo "kubectl:        $(kubectl version --client --short 2>/dev/null || true)"
    echo "kubeadm:        $(kubeadm version -o short 2>/dev/null || true)"
    echo "Helm:           $(helm version --short)"
    echo "SonarScanner:   $(sonar-scanner --version 2>/dev/null | head -1 || true)"
    echo ""
    echo "=================================================="
    echo " SmartLingua DevOps environment ready!"
    echo "   Project at: /home/vagrant/smartlingua"
    echo "   Run: cd /home/vagrant/smartlingua && docker compose up -d"
    echo "=================================================="
  SHELL

  # ── Post-provision: start stack as vagrant user ─────────────────────────────
  config.vm.provision "shell", privileged: false, name: "start-stack", inline: <<-SHELL
    cd /home/vagrant/smartlingua
    echo "SmartLingua is ready. To start the full stack:"
    echo "  cd ~/smartlingua && docker compose up -d"
  SHELL

end
