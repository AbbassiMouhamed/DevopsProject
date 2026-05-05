# SmartLingua — Vagrant Guide

## Overview

The `Vagrantfile` provisions a single **Ubuntu 22.04 LTS** virtual machine with the complete DevOps toolchain pre-installed.

### What's Included

| Tool                   | Version       |
| ---------------------- | ------------- |
| Docker Engine          | latest stable |
| Docker Compose v2      | latest stable |
| Java (Eclipse Temurin) | 21            |
| Maven                  | 3.9           |
| Node.js                | 20 LTS        |
| kubectl                | 1.30          |
| kubeadm + kubelet      | 1.30          |
| Helm                   | 3.x           |
| SonarScanner CLI       | 6.x           |
| Git, curl, wget, unzip | system        |

### VM Resources

| Resource   | Value                             |
| ---------- | --------------------------------- |
| OS         | Ubuntu 22.04 LTS (ubuntu/jammy64) |
| RAM        | 6 GB                              |
| CPUs       | 4                                 |
| Private IP | 192.168.56.10                     |

---

## Prerequisites

1. Install [VirtualBox 7.x](https://www.virtualbox.org/wiki/Downloads)
2. Install [Vagrant 2.4+](https://developer.hashicorp.com/vagrant/downloads)

```bash
# Verify installations
vagrant --version
vboxmanage --version
```

---

## Usage

```bash
# From the project root:

# First time: provision VM (~10 minutes, downloads ~2 GB)
vagrant up

# SSH into the VM
vagrant ssh

# Inside the VM: start the full SmartLingua stack
cd ~/smartlingua
docker compose up -d --build

# Check services
docker compose ps
```

### Common Commands

```bash
vagrant up          # Start and provision
vagrant halt        # Graceful shutdown
vagrant reload      # Restart (apply config changes)
vagrant suspend     # Hibernate to disk
vagrant resume      # Resume from hibernate
vagrant destroy -f  # Delete VM completely
vagrant status      # Check VM state
vagrant provision   # Re-run provisioner on a running VM
```

---

## Accessing Services from Host

Once the stack is running inside the VM, services are accessible from your host machine via forwarded ports:

| Service     | URL                   |
| ----------- | --------------------- |
| Frontend    | http://localhost:4200 |
| API Gateway | http://localhost:8093 |
| Eureka      | http://localhost:8761 |
| Keycloak    | http://localhost:8081 |
| Prometheus  | http://localhost:9090 |
| Grafana     | http://localhost:3000 |

Or via the VM's private IP: `http://192.168.56.10:<port>`

---

## Project Files Inside VM

The project directory is synced into the VM at `/home/vagrant/smartlingua`:

```
/home/vagrant/smartlingua/   ←→   (project root on host)
```

Changes made on the host are immediately visible inside the VM and vice versa.

---

## Troubleshooting

**`vagrant up` hangs on "Waiting for machine to boot"**  
Enable VT-x/AMD-V virtualisation in your BIOS settings.

**Port already allocated**  
Stop any local service using the conflicting port, or edit the `host:` port in the `Vagrantfile`.

**Shared folder mounting fails**  
Install VirtualBox Guest Additions:

```bash
vagrant plugin install vagrant-vbguest
vagrant destroy -f && vagrant up
```

**Out of disk space inside VM**  
The default VirtualBox dynamic disk is 40 GB. Docker images for the full stack require ~10 GB.
