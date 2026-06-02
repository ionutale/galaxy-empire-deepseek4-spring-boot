package game

import "testing"

func TestBalancerMatchesJava(t *testing.T) {
	b := NewBalancer(1)

	if got := b.MetalCost(MetalMine, 1); got != 60 {
		t.Errorf("MetalCost(METAL_MINE,1)=%v want 60", got)
	}
	if got := b.CrystalCost(CrystalMine, 1); got != 24 {
		t.Errorf("CrystalCost(CRYSTAL_MINE,1)=%v want 24", got)
	}
	if got := b.ShipMetalCost(Battleship); got != 15000 {
		t.Errorf("ShipMetalCost(BATTLESHIP)=%v want 15000", got)
	}
	if got := b.ShipHull(Cruiser); got != 2700 {
		t.Errorf("ShipHull(CRUISER)=%v want 2700", got)
	}
	if got := b.StorageCapacity(1); got != 5000 {
		t.Errorf("StorageCapacity(1)=%v want 5000", got)
	}
	// Construction time floors to a 10s minimum and divides by (1+rfLevel).
	if got := b.ConstructionTimeSeconds(MetalStorage, 1, 0); got != 1200 {
		t.Errorf("ConstructionTimeSeconds(METAL_STORAGE,1,0)=%v want 1200", got)
	}
}

func TestSpeedUpCost(t *testing.T) {
	cases := map[int64]int{0: 0, -5: 0, 1: 1, 1800: 1, 1801: 2, 3600: 2}
	for remaining, want := range cases {
		if got := calculateSpeedUpCost(remaining); got != want {
			t.Errorf("calculateSpeedUpCost(%d)=%d want %d", remaining, got, want)
		}
	}
}

func TestRapidFire(t *testing.T) {
	rf := NewBalancer(1).RapidFire()
	if rf[Battleship][Cruiser] != 2 {
		t.Errorf("battleship rapid fire vs cruiser = %d want 2", rf[Battleship][Cruiser])
	}
}
