# Money Manager Backend - Deployment Guide

## Continuous Deployment (GitHub Actions)

This is how the app **actually** ships today — every push to `main` triggers a fully automated build-and-deploy. Workflow file: `.github/workflows/deploy.yml`.

### Pipeline flow

```
        push to main
            │
            ▼
┌─────────────────────────┐
│ 1. Checkout source      │  actions/checkout@v3
└────────────┬────────────┘
             ▼
┌─────────────────────────┐
│ 2. Set up JDK 21        │  actions/setup-java@v4 (temurin, maven cache)
└────────────┬────────────┘
             ▼
┌─────────────────────────┐
│ 3. mvn clean package    │  -DskipTests=true → produces
│    -DskipTests=true     │  target/moneymanager-0.0.1-SNAPSHOT.jar
└────────────┬────────────┘
             ▼
┌─────────────────────────┐
│ 4. docker build         │  builds image from the repo Dockerfile,
│    montrax:latest       │  which just COPYs the jar built in step 3
└────────────┬────────────┘
             ▼
┌─────────────────────────┐
│ 5. Login to DockerHub   │  docker/login-action@v4
└────────────┬────────────┘
             ▼
┌─────────────────────────┐
│ 6. docker push :latest  │  publishes image to DockerHub
└────────────┬────────────┘
             ▼
┌─────────────────────────────────────────────┐
│ 7. SSH into Oracle Cloud instance           │  appleboy/ssh-action@v1.2.0
│    (OCI_HOST / OCI_USER / OCI_SSH_KEY)      │
│                                             │
│    docker pull   <image>:latest             │
│    docker stop   <OCI_CONTAINER_NAME>       │
│    docker rm     <OCI_CONTAINER_NAME>       │
│    docker run -d --name <OCI_CONTAINER_NAME>│
│      --restart unless-stopped               │
│      --env-file /home/<user>/apps/app2/.env │
│      -p 8090:8090 <image>:latest            │
│    docker image prune -f                    │
└─────────────────────────────────────────────┘
```

The whole pipeline is push-to-deploy: merging to `main` is the deploy button. There is no staging environment, no manual approval gate, and no separate build step for pull requests — the workflow only triggers `on: push: branches: [main]`.

### Required GitHub Secrets

| Secret | Used for |
|---|---|
| `DOCKER_USERNAME` / `DOCKER_PASSWORD` | DockerHub login + image namespace |
| `OCI_HOST` | Oracle Cloud instance address for SSH |
| `OCI_USER` | SSH login user |
| `OCI_SSH_KEY` | Private key for SSH auth |
| `OCI_CONTAINER_NAME` | Name of the running container to stop/replace |

The `.env` file consumed by `--env-file` lives directly on the server at `/home/<OCI_USER>/apps/app2/.env` and is **not** managed by this pipeline — it's created once during initial server setup (see the manual steps below) and edited by hand on the box whenever a secret rotates. The pipeline only ever changes the *image*, never the environment configuration.

### What this pipeline does **not** do — read before relying on it

These are real gaps in the current setup, worth knowing before treating a green CI run as "safely deployed":

- **No rollback tag.** Every build pushes to the same `:latest` tag, overwriting the previous image. If a bad build ships, there's no `:previous` tag to roll back to on DockerHub — recovery means finding an older local image on the server (if `docker image prune -f` hasn't already removed it) or re-running the pipeline from a known-good commit.
- **No health-gated cutover.** The deploy script stops and removes the *old* container **before** confirming the *new* one is healthy. If the new image fails to start (bad config, crash on boot), the app is down with nothing automatically reverting it — there's a real window of downtime on every deploy, and a broken deploy doesn't self-heal.
- **Tests are skipped** (`-DskipTests=true`) — the build never runs the test suite before shipping, trading safety for build speed.
- **No smoke test after deploy.** The workflow doesn't curl `/actuator/health` (or anything else) after `docker run` to confirm the container actually came up — a failed deploy can still show green in GitHub Actions.
- **Plain `docker run`, not `docker compose up`.** Production does not use `docker-compose.yml` at all — the memory/CPU `deploy.resources.limits` in that file are a local-dev/reference-only concern and are **not applied** to the container that actually runs in production. Resource constraints in production come only from whatever's set inside the image's `JAVA_OPTS` (baked into the Dockerfile) plus whatever the host VM naturally enforces.
- **Kafka/RabbitMQ are never started by this pipeline** — consistent with the app's design (see main `README.md`), production is expected to point at managed brokers via env vars, not the `docker compose --profile infra` local containers.

### Suggested hardening (not yet implemented)

If/when this pipeline gets revisited, the highest-value changes in rough priority order:
1. Tag images by commit SHA in addition to `:latest`, so a rollback is `docker run <previous-sha>` instead of a fresh build.
2. Health-check the new container (`curl` retry loop against `/actuator/health`) before removing the old one — start the new container on a temp port, verify, then swap.
3. Run `./mvnw test` in CI (drop `-DskipTests`) so a broken build never reaches DockerHub in the first place.
4. Add a post-deploy smoke test step that fails the workflow (and ideally auto-rolls-back) if the health check doesn't pass within N retries.

---

## Oracle Cloud Free Tier AMD Server Deployment

*(The steps below are for provisioning the server the first time, or recovering it by hand. Day-to-day deploys happen automatically via the GitHub Actions pipeline above once the server and its `.env` file exist.)*

### Server Specifications & Capacity
Your server has:
- **RAM:** ~1GB (956Mi total, ~423Mi free)
- **Disk:** 45GB (40GB available)
- **Swap:** 1GB

**Can you run this app?** ✅ YES, but with optimizations.

With nginx and your old app already running, you have ~400MB free. This backend is optimized to run in ~250-300MB, leaving headroom for spikes.

**Expected capacity:**
- ~10-20 concurrent users comfortably
- ~50-100 daily active users
- Database/Redis/MongoDB are external, so the server only handles API requests

### Step-by-Step Oracle Cloud Deployment

#### 1. Connect to Your Server
```bash
ssh -i your-key.pem ubuntu@your-server-ip
```

#### 2. Create Application Directory
```bash
sudo mkdir -p /opt/moneymanager
sudo chown $USER:$USER /opt/moneymanager
cd /opt/moneymanager
```

#### 3. Upload Application Files

**Option A: Git Clone**
```bash
git clone your-repo-url .
cd moneymanager
```

**Option B: SCP from local machine**
```bash
# Run from your local machine:
scp -i your-key.pem -r ./moneymanager/* ubuntu@your-server-ip:/opt/moneymanager/
```

#### 4. Create Environment File
```bash
cp .env.example .env
nano .env
# Fill in all your credentials
```

#### 5. Build and Deploy
```bash
# Build the Docker image (may take 5-10 minutes first time)
docker compose build

# Start the application
docker compose up -d

# Check if it's running
docker compose ps
docker compose logs -f
```

#### 6. Configure Nginx Reverse Proxy

Create new config file:
```bash
sudo nano /etc/nginx/sites-available/moneymanager-api
```

Add this configuration:
```nginx
server {
    listen 80;
    server_name api.yourdomain.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name api.yourdomain.com;
    
    ssl_certificate /etc/letsencrypt/live/api.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.yourdomain.com/privkey.pem;
    
    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    
    location / {
        proxy_pass http://127.0.0.1:8090;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # Timeouts
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
        
        # File upload size (for profile images)
        client_max_body_size 10M;
    }
}
```

Enable the site:
```bash
sudo ln -s /etc/nginx/sites-available/moneymanager-api /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

#### 7. Get SSL Certificate
```bash
sudo certbot --nginx -d api.yourdomain.com
```

#### 8. Open Firewall Port in Oracle Cloud
In Oracle Cloud Console:
1. Go to Networking → Virtual Cloud Networks
2. Click your VCN → Security Lists
3. Add Ingress Rule: Port 443, Source 0.0.0.0/0

Also in Ubuntu firewall:
```bash
sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT
sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT
```

---

## Server Requirements
- **RAM:** Minimum 1GB (optimized for low memory)
- **Docker:** Already installed
- **Nginx:** Already installed

## Quick Deployment Steps

### 1. Clone/Upload Your Code to Server
```bash
# Option A: Git clone
git clone your-repo-url /opt/moneymanager-backend
cd /opt/moneymanager-backend/moneymanager

# Option B: SCP upload
scp -r ./moneymanager user@your-server:/opt/moneymanager-backend/
```

### 2. Create Environment File
```bash
cd /opt/moneymanager-backend/moneymanager

# Copy example and edit with your values
cp .env.example .env
nano .env
```

### 3. Build and Run
```bash
# Build the image
docker compose build

# Start in background
docker compose up -d

# Check logs
docker compose logs -f
```

### 4. Verify Deployment
```bash
# Check container status
docker compose ps

# Check health
curl http://localhost:8090/actuator/health

# Monitor resources
docker stats moneymanager-backend
```

## Nginx Configuration

Add this to your Nginx config (`/etc/nginx/sites-available/default` or create new file):

```nginx
server {
    listen 80;
    server_name api.yourdomain.com;
    
    # Redirect HTTP to HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name api.yourdomain.com;
    
    # SSL certificates (use Let's Encrypt)
    ssl_certificate /etc/letsencrypt/live/api.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.yourdomain.com/privkey.pem;
    
    # Proxy to backend
    location / {
        proxy_pass http://localhost:8090;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
        
        # Timeouts for slow operations
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
    
    # Health check endpoint
    location /actuator/health {
        proxy_pass http://localhost:8090;
        access_log off;
    }
}
```

### Enable site and reload Nginx:
```bash
# If using sites-available pattern
sudo ln -s /etc/nginx/sites-available/moneymanager /etc/nginx/sites-enabled/

# Test and reload
sudo nginx -t
sudo systemctl reload nginx
```

## SSL Certificate (Let's Encrypt)
```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d api.yourdomain.com
```

## Useful Commands

```bash
# View logs
docker compose logs -f moneymanager-backend

# Restart application
docker compose restart

# Stop application
docker compose down

# Rebuild and restart
docker compose down && docker compose build --no-cache && docker compose up -d

# Monitor memory usage
free -h
docker stats --no-stream

# Check application health
curl http://localhost:8090/actuator/health
```

## Memory Optimization Tips

If running out of memory:

1. **Reduce JVM heap:**
   Edit `docker-compose.yml`:
   ```yaml
   JAVA_OPTS: "-Xmx192m -Xms96m ..."
   ```

2. **Reduce container memory limit:**
   ```yaml
   limits:
     memory: 300M
   ```

3. **Add swap if needed:**
   ```bash
   sudo fallocate -l 1G /swapfile
   sudo chmod 600 /swapfile
   sudo mkswap /swapfile
   sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   ```

## Troubleshooting

### Container keeps restarting
```bash
docker compose logs --tail=100 moneymanager-backend
```

### Out of memory
```bash
# Check memory
free -h

# Check what's using memory
docker stats
ps aux --sort=-%mem | head -10
```

### Application not responding
```bash
# Check if container is healthy
docker compose ps

# Check if port is listening
netstat -tlnp | grep 8090
```

