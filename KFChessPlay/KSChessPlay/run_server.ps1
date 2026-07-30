# DEPRECATED (PLAN_split_6_services.md §3.7): there is no single "the server" process anymore as of
# §3.3-§3.5 - server.ServerController (classifier "server") was deleted outright once splitting
# Matchmaking/room-allocation/login out of it left it with zero responsibilities (see
# PLAN_split_6_services.md §6 and docker-compose.yml's own comments for the full story). The backend
# is now 5 independent processes (Shard/Gateway/Matchmaker/Allocator/API Gateway) plus NATS/Redis/
# Postgres - run all of them together with:
#
#     docker compose up --build
#
# This script is kept (not deleted) only so a stale reference to it fails loudly with a pointer to
# the replacement, rather than silently building a jar classifier that no longer exists.
Write-Host "run_server.ps1 is deprecated - there is no single server process anymore." -ForegroundColor Yellow
Write-Host "Run the full backend with: docker compose up --build" -ForegroundColor Yellow
Write-Host "(see docker-compose.yml / PLAN_split_6_services.md for the 5-process breakdown)" -ForegroundColor Yellow
exit 1
