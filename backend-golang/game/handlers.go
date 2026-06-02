package game

import (
	"context"
	"embed"
	"encoding/json"
	"errors"
	"net/http"
	"strconv"

	"galaxyempire/internal/database"
	"galaxyempire/internal/stomp"
	"galaxyempire/internal/web"

	"github.com/jackc/pgx/v5/pgxpool"
)

//go:embed migrations/*.sql
var Migrations embed.FS

// MigrateDB runs the game migrations.
func MigrateDB(ctx context.Context, pool *pgxpool.Pool) error {
	return database.Migrate(ctx, pool, Migrations, "planet")
}

// RegisterRoutes wires every /api/game endpoint (and the /ws STOMP endpoint)
// onto mux, matching the Spring controllers' routes.
func (e *Engine) RegisterRoutes(mux *http.ServeMux, broker *stomp.Broker) {
	// PlanetController
	mux.HandleFunc("GET /api/game/planets/my", e.hGetMyPlanets)
	mux.HandleFunc("GET /api/game/planets/{id}", e.hGetPlanet)
	mux.HandleFunc("POST /api/game/planets", e.hCreatePlanet)
	mux.HandleFunc("GET /api/game/planets/{id}/resources", e.hGetResources)
	mux.HandleFunc("GET /api/game/players/{playerId}/dark-matter", e.hGetDarkMatter)
	mux.HandleFunc("POST /api/game/players/{playerId}/dark-matter/add", e.hAddDarkMatter)
	mux.HandleFunc("POST /api/game/planets/{planetId}/buildings/queue/{queueId}/speed-up", e.hSpeedUpBuilding)
	mux.HandleFunc("POST /api/game/planets/{planetId}/shipyard/{queueId}/speed-up", e.hSpeedUpShipyard)

	// BuildingController
	mux.HandleFunc("POST /api/game/planets/{planetId}/buildings/{gridPosition}/upgrade", e.hUpgradeBuilding)
	mux.HandleFunc("GET /api/game/planets/{planetId}/buildings/{gridPosition}/cost", e.hUpgradeCost)
	mux.HandleFunc("GET /api/game/planets/{planetId}/queue", e.hGetQueue)

	// FleetController
	mux.HandleFunc("POST /api/game/planets/{planetId}/fleet", e.hLaunchFleet)
	mux.HandleFunc("GET /api/game/planets/{planetId}/fleet", e.hGetPlanetFleets)
	mux.HandleFunc("GET /api/game/fleet/{fleetId}", e.hGetFleetDetail)
	mux.HandleFunc("POST /api/game/fleet/{fleetId}/recall", e.hRecallFleet)
	mux.HandleFunc("GET /api/game/planets/{planetId}/combat-reports", e.hCombatReports)
	mux.HandleFunc("GET /api/game/combat-reports/{reportId}", e.hCombatReport)
	mux.HandleFunc("GET /api/game/planets/{planetId}/debris", e.hDebris)
	mux.HandleFunc("GET /api/game/planets/{planetId}/espionage-reports", e.hEspionage)

	// GalaxyController
	mux.HandleFunc("GET /api/game/galaxies/{galaxy}/systems", e.hSystemList)
	mux.HandleFunc("GET /api/game/galaxies/{galaxy}/systems/{systemId}", e.hSystemDetail)

	// ShipyardController
	mux.HandleFunc("GET /api/game/planets/{planetId}/ships", e.hPlanetShips)
	mux.HandleFunc("GET /api/game/planets/{planetId}/shipyard", e.hShipTypes)
	mux.HandleFunc("POST /api/game/planets/{planetId}/ships/{type}/build", e.hBuildShips)
	mux.HandleFunc("GET /api/game/planets/{planetId}/shipyard-queue", e.hShipyardQueue)
	mux.HandleFunc("GET /api/game/planets/{planetId}/defense-types", e.hDefenseTypes)
	mux.HandleFunc("POST /api/game/planets/{planetId}/defense", e.hBuildDefense)
	mux.HandleFunc("GET /api/game/planets/{planetId}/defenses", e.hPlanetDefenses)

	// TechnologyController
	mux.HandleFunc("GET /api/game/technologies", e.hTechnologies)
	mux.HandleFunc("POST /api/game/technologies/speed-up", e.hSpeedUpResearch)
	mux.HandleFunc("GET /api/game/technologies/{name}", e.hTechnology)
	mux.HandleFunc("POST /api/game/technologies/{name}/research", e.hStartResearch)
	mux.HandleFunc("GET /api/game/research-queue", e.hResearchQueue)

	// QuestController
	mux.HandleFunc("GET /api/game/quests", e.hQuests)
	mux.HandleFunc("POST /api/game/quests/{progressId}/claim", e.hClaimQuest)

	// WebSocket (STOMP). The frontend connects to /ws/websocket via the gateway.
	if broker != nil {
		mux.HandleFunc("/ws/websocket", broker.HandleWebSocket)
		mux.HandleFunc("/ws", broker.HandleWebSocket)
	}
}

// ---- helpers ----

func playerID(r *http.Request) int64 {
	id, _ := strconv.ParseInt(r.Header.Get("X-Player-Id"), 10, 64)
	return id
}

func pathInt64(r *http.Request, name string) int64 {
	v, _ := strconv.ParseInt(r.PathValue(name), 10, 64)
	return v
}

func pathInt(r *http.Request, name string) int {
	v, _ := strconv.Atoi(r.PathValue(name))
	return v
}

func decode(r *http.Request, v any) error { return json.NewDecoder(r.Body).Decode(v) }

func fail(w http.ResponseWriter, err error) {
	var ae AppError
	if errors.As(err, &ae) {
		web.Error(w, http.StatusBadRequest, ae.Msg)
		return
	}
	web.Error(w, http.StatusInternalServerError, err.Error())
}

func ok(w http.ResponseWriter, v any) { web.JSON(w, http.StatusOK, v) }

// ---- PlanetController ----

func (e *Engine) hGetPlanet(w http.ResponseWriter, r *http.Request) {
	details, err := e.getPlanetDetails(r.Context(), pathInt64(r, "id"))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, details)
}

func (e *Engine) hCreatePlanet(w http.ResponseWriter, r *http.Request) {
	planet, err := e.CreateStarterPlanet(r.Context(), playerID(r))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, map[string]any{"id": planet.ID, "name": planet.Name, "coordinates": coords(planet)})
}

func (e *Engine) hGetMyPlanets(w http.ResponseWriter, r *http.Request) {
	planets, err := e.getPlanetsByPlayer(r.Context(), playerID(r))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, planets)
}

func (e *Engine) hGetResources(w http.ResponseWriter, r *http.Request) {
	res, err := e.getCurrentResources(r.Context(), pathInt64(r, "id"))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, res)
}

func (e *Engine) hGetDarkMatter(w http.ResponseWriter, r *http.Request) {
	ok(w, map[string]any{"darkMatter": e.GetDarkMatter(r.Context(), pathInt64(r, "playerId"))})
}

func (e *Engine) hAddDarkMatter(w http.ResponseWriter, r *http.Request) {
	if !e.debugEndpoints {
		web.Error(w, http.StatusForbidden, "Debug endpoint disabled")
		return
	}
	var body map[string]int
	_ = decode(r, &body)
	pid := pathInt64(r, "playerId")
	if err := e.AddDarkMatter(r.Context(), pid, body["amount"]); err != nil {
		fail(w, err)
		return
	}
	ok(w, map[string]any{"darkMatter": e.GetDarkMatter(r.Context(), pid)})
}

func (e *Engine) hSpeedUpBuilding(w http.ResponseWriter, r *http.Request) {
	if err := e.SpeedUpConstruction(r.Context(), pathInt64(r, "queueId"), playerID(r)); err != nil {
		fail(w, err)
		return
	}
	ok(w, map[string]any{"success": true})
}

func (e *Engine) hSpeedUpShipyard(w http.ResponseWriter, r *http.Request) {
	if err := e.SpeedUpShipyardEntry(r.Context(), pathInt64(r, "queueId"), playerID(r)); err != nil {
		fail(w, err)
		return
	}
	ok(w, map[string]any{"success": true})
}

// ---- BuildingController ----

func (e *Engine) hUpgradeBuilding(w http.ResponseWriter, r *http.Request) {
	res, err := e.QueueUpgrade(r.Context(), pathInt64(r, "planetId"), pathInt(r, "gridPosition"), playerID(r))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, res)
}

func (e *Engine) hUpgradeCost(w http.ResponseWriter, r *http.Request) {
	res, err := e.GetUpgradeCost(r.Context(), pathInt64(r, "planetId"), pathInt(r, "gridPosition"))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, res)
}

func (e *Engine) hGetQueue(w http.ResponseWriter, r *http.Request) {
	q, err := e.ConstructionQueueByPlanet(r.Context(), pathInt64(r, "planetId"))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, q)
}

// ---- FleetController ----

func (e *Engine) hLaunchFleet(w http.ResponseWriter, r *http.Request) {
	var body map[string]any
	if err := decode(r, &body); err != nil {
		web.Error(w, http.StatusBadRequest, "Invalid request body")
		return
	}
	missionRaw, _ := body["mission"].(string)
	mission := missionRaw
	ships := shipsFromBody(body["ships"])
	pid := playerID(r)
	planetID := pathInt64(r, "planetId")

	if mission == MissionColonize {
		galaxy := intFromAny(body["galaxy"])
		systemID := intFromAny(body["systemId"])
		slot := intFromAny(body["slot"])
		planet, err := e.CreatePlanetAt(r.Context(), pid, galaxy, systemID, slot)
		if err != nil {
			web.Error(w, http.StatusBadRequest, err.Error())
			return
		}
		res, err := e.LaunchFleet(r.Context(), planetID, planet.ID, mission, ships, pid, body)
		if err != nil {
			web.Error(w, http.StatusBadRequest, err.Error())
			return
		}
		ok(w, res)
		return
	}

	targetPlanetID := int64FromAny(body["targetPlanetId"])
	res, err := e.LaunchFleet(r.Context(), planetID, targetPlanetID, mission, ships, pid, body)
	if err != nil {
		web.Error(w, http.StatusBadRequest, err.Error())
		return
	}
	ok(w, res)
}

func (e *Engine) hGetPlanetFleets(w http.ResponseWriter, r *http.Request) {
	f, err := e.GetPlanetFleets(r.Context(), pathInt64(r, "planetId"))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, f)
}

func (e *Engine) hGetFleetDetail(w http.ResponseWriter, r *http.Request) {
	d, err := e.GetFleetDetail(r.Context(), pathInt64(r, "fleetId"))
	if err != nil {
		web.Error(w, http.StatusBadRequest, err.Error())
		return
	}
	ok(w, d)
}

func (e *Engine) hRecallFleet(w http.ResponseWriter, r *http.Request) {
	if err := e.RecallFleet(r.Context(), pathInt64(r, "fleetId"), playerID(r)); err != nil {
		web.Error(w, http.StatusBadRequest, err.Error())
		return
	}
	ok(w, map[string]any{"status": "recalled"})
}

func (e *Engine) hCombatReports(w http.ResponseWriter, r *http.Request) {
	reps, err := e.GetPlanetCombatReports(r.Context(), pathInt64(r, "planetId"))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, reps)
}

func (e *Engine) hCombatReport(w http.ResponseWriter, r *http.Request) {
	rep, err := e.GetCombatReport(r.Context(), pathInt64(r, "reportId"))
	if err != nil {
		web.Error(w, http.StatusBadRequest, err.Error())
		return
	}
	ok(w, rep)
}

func (e *Engine) hDebris(w http.ResponseWriter, r *http.Request) {
	df, err := e.GetDebrisField(r.Context(), pathInt64(r, "planetId"))
	if err != nil {
		fail(w, err)
		return
	}
	if df == nil {
		ok(w, map[string]any{"metal": 0, "crystal": 0})
		return
	}
	ok(w, df)
}

func (e *Engine) hEspionage(w http.ResponseWriter, r *http.Request) {
	reps, err := e.GetPlanetEspionageReports(r.Context(), pathInt64(r, "planetId"))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, reps)
}

// ---- GalaxyController ----

func (e *Engine) hSystemList(w http.ResponseWriter, r *http.Request) {
	list, err := e.GetSystemList(r.Context(), pathInt(r, "galaxy"), playerID(r))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, list)
}

func (e *Engine) hSystemDetail(w http.ResponseWriter, r *http.Request) {
	d, err := e.GetSystemDetail(r.Context(), pathInt(r, "galaxy"), pathInt(r, "systemId"), playerID(r))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, d)
}

// ---- ShipyardController ----

func (e *Engine) hPlanetShips(w http.ResponseWriter, r *http.Request) {
	s, err := e.GetPlanetShips(r.Context(), pathInt64(r, "planetId"))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, s)
}

func (e *Engine) hShipTypes(w http.ResponseWriter, r *http.Request) {
	s, err := e.GetShipTypes(r.Context(), pathInt64(r, "planetId"))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, s)
}

func (e *Engine) hBuildShips(w http.ResponseWriter, r *http.Request) {
	var body map[string]int
	_ = decode(r, &body)
	quantity := 1
	if q, ok2 := body["quantity"]; ok2 {
		quantity = q
	}
	res, err := e.BuildShips(r.Context(), pathInt64(r, "planetId"), upper(r.PathValue("type")), quantity, playerID(r))
	if err != nil {
		web.Error(w, http.StatusBadRequest, err.Error())
		return
	}
	ok(w, res)
}

func (e *Engine) hShipyardQueue(w http.ResponseWriter, r *http.Request) {
	q, err := e.GetShipyardQueue(r.Context(), pathInt64(r, "planetId"))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, q)
}

func (e *Engine) hDefenseTypes(w http.ResponseWriter, r *http.Request) {
	d, err := e.GetDefenseTypes(r.Context(), pathInt64(r, "planetId"))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, d)
}

func (e *Engine) hBuildDefense(w http.ResponseWriter, r *http.Request) {
	var body map[string]any
	if err := decode(r, &body); err != nil {
		web.Error(w, http.StatusBadRequest, err.Error())
		return
	}
	dt, _ := body["defenseType"].(string)
	quantity := intFromAny(body["quantity"])
	res, err := e.BuildDefense(r.Context(), pathInt64(r, "planetId"), upper(dt), quantity, playerID(r))
	if err != nil {
		web.Error(w, http.StatusBadRequest, err.Error())
		return
	}
	ok(w, res)
}

func (e *Engine) hPlanetDefenses(w http.ResponseWriter, r *http.Request) {
	d, err := e.GetPlanetDefenses(r.Context(), pathInt64(r, "planetId"))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, d)
}

// ---- TechnologyController ----

func (e *Engine) hTechnologies(w http.ResponseWriter, r *http.Request) {
	t, err := e.GetTechnologies(r.Context(), playerID(r))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, t)
}

func (e *Engine) hTechnology(w http.ResponseWriter, r *http.Request) {
	name := upper(r.PathValue("name"))
	if !isTechnology(name) {
		web.Error(w, http.StatusBadRequest, "Unknown technology: "+r.PathValue("name"))
		return
	}
	t, err := e.GetTechnologyDetails(r.Context(), playerID(r), name)
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, t)
}

func (e *Engine) hStartResearch(w http.ResponseWriter, r *http.Request) {
	name := upper(r.PathValue("name"))
	if !isTechnology(name) {
		web.Error(w, http.StatusBadRequest, "Unknown technology")
		return
	}
	res, err := e.StartResearch(r.Context(), playerID(r), name)
	if err != nil {
		web.Error(w, http.StatusBadRequest, err.Error())
		return
	}
	ok(w, res)
}

func (e *Engine) hResearchQueue(w http.ResponseWriter, r *http.Request) {
	q, err := e.GetActiveResearch(r.Context(), playerID(r))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, q)
}

func (e *Engine) hSpeedUpResearch(w http.ResponseWriter, r *http.Request) {
	var body map[string]string
	_ = decode(r, &body)
	if err := e.SpeedUpResearch(r.Context(), playerID(r), body["technology"]); err != nil {
		web.Error(w, http.StatusBadRequest, err.Error())
		return
	}
	ok(w, map[string]any{"success": true})
}

// ---- QuestController ----

func (e *Engine) hQuests(w http.ResponseWriter, r *http.Request) {
	q, err := e.GetAvailableQuests(r.Context(), playerID(r))
	if err != nil {
		fail(w, err)
		return
	}
	ok(w, q)
}

func (e *Engine) hClaimQuest(w http.ResponseWriter, r *http.Request) {
	res, err := e.ClaimReward(r.Context(), playerID(r), pathInt64(r, "progressId"))
	if err != nil {
		web.Error(w, http.StatusBadRequest, err.Error())
		return
	}
	ok(w, res)
}

// ---- body parsing helpers ----

func shipsFromBody(v any) map[string]int {
	out := map[string]int{}
	m, ok := v.(map[string]any)
	if !ok {
		return out
	}
	for k, val := range m {
		out[k] = intFromAny(val)
	}
	return out
}

func intFromAny(v any) int {
	switch n := v.(type) {
	case float64:
		return int(n)
	case int:
		return n
	case string:
		i, _ := strconv.Atoi(n)
		return i
	}
	return 0
}

func int64FromAny(v any) int64 {
	switch n := v.(type) {
	case float64:
		return int64(n)
	case int64:
		return n
	case string:
		i, _ := strconv.ParseInt(n, 10, 64)
		return i
	}
	return 0
}

func upper(s string) string {
	b := []byte(s)
	for i, c := range b {
		if c >= 'a' && c <= 'z' {
			b[i] = c - 32
		}
	}
	return string(b)
}

func isTechnology(name string) bool {
	for _, t := range Technologies {
		if t == name {
			return true
		}
	}
	return false
}
