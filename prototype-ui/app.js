(() => {
  const host = document.getElementById("globe");
  if (!host || typeof Globe !== "function") return;

  const globe = Globe()(host)
    .backgroundColor("rgba(0,0,0,0)")
    .showAtmosphere(true)
    .atmosphereColor("#777777")
    .atmosphereAltitude(0.12)
    .showGraticules(false)
    .showGlobe(true)
    .globeMaterial({
      color: "#171717",
      roughness: 0.92,
      metalness: 0.08
    })
    .polygonCapColor(() => "rgba(115,115,115,0.13)")
    .polygonSideColor(() => "rgba(30,30,30,0.3)")
    .polygonStrokeColor(() => "rgba(190,190,190,0.24)")
    .polygonAltitude(0.006)
    .polygonsTransitionDuration(0);

  globe.controls().autoRotate = true;
  globe.controls().autoRotateSpeed = 0.22;
  globe.controls().enableDamping = true;
  globe.controls().dampingFactor = 0.08;
  globe.controls().minDistance = 115;
  globe.controls().maxDistance = 420;

  fetch("https://unpkg.com/world-atlas@2/countries-110m.json")
    .then(response => {
      if (!response.ok) throw new Error("Données géographiques indisponibles");
      return response.json();
    })
    .then(world => {
      const countries = topojson.feature(world, world.objects.countries).features;
      globe.polygonsData(countries);
    })
    .catch(() => {
      // Le globe reste affiché même si les contours pays ne chargent pas.
    });

  const resize = () => {
    const width = host.clientWidth;
    const height = host.clientHeight;
    if (width && height) globe.width(width).height(height);
  };
  if ("ResizeObserver" in window) {
    new ResizeObserver(resize).observe(host);
  } else {
    window.addEventListener("resize", resize);
  }
  resize();
})();