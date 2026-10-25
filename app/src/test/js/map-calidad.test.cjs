const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const html = fs.readFileSync(path.join(__dirname, '../../main/assets/explore-map.html'), 'utf8');
const labels = {loading:'cargando',error:'error',slow:'lento',empty:'vacio',origin:'origen',position:'posicion',locals:'locales',route:'ruta'};
const code = html.replace('/*INITIAL_LABELS*/', JSON.stringify(labels)).match(/<script>\s*(const labels =[\s\S]+?)<\/script>/)[1];

function app({dark = false, ratio = 3, failGL = false, vector = true} = {}) {
    let fits = 0, removed = 0, workers;
    const events = {}, canvasEvents = {}, timers = [], raster = [], styles = [], vectorOptions = [], paints = [], fitOptions = [];
    const canvas = {width:800,clientWidth:400,addEventListener(name, fn){canvasEvents[name] = fn}};
    const gl = {on(name,fn){events[name]=fn;return this},getCanvas:()=>canvas,getPixelRatio:()=>Math.min(ratio,2),setStyle(style){styles.push(style)},
        getStyle:()=>({layers:[{id:'background',type:'background'},{id:'water',type:'fill','source-layer':'water'},
            {id:'highway_minor',type:'line','source-layer':'transportation'},
            {id:'highway_name_other',type:'symbol','source-layer':'transportation_name',layout:{'text-field':'name'}}]}),
        setPaintProperty(id,property,value){paints.push({id,property,value})}};
    const map = {setView(){return this},fitBounds(bounds,options){fits++;fitOptions.push(options)},getSize:()=>({x:360,y:640}),invalidateSize(){},on(){return this},removeLayer(){removed++}};
    const elements = {status:{},me:{},map:{style:{}},controls:{}};
    const ctx = {window:{devicePixelRatio:ratio,location:{}},document:{
        documentElement:{dataset:{theme:dark?'dark':'light'},style:{setProperty(){}}},body:{style:{}},
        getElementById:id=>elements[id],querySelector:()=>elements.controls,createElement:()=>({textContent:''})},
        setTimeout(fn,ms){timers.push({fn,ms})},L:{map:()=>map,control:{zoom:()=>({addTo(){}})},
            tileLayer(url,options){const tile={url,options,events:{},addTo(){return this},on(name,fn){this.events[name]=fn;return this}};raster.push(tile);return tile},
            layerGroup:()=>({addTo(){return this},clearLayers(){}}),
            circleMarker:()=>({addTo(){return this},bindTooltip(){return this},on(){return this},openTooltip(){return this}})}};
    if(vector) {
        ctx.maplibregl = {setWorkerCount(value){workers=value}};
        ctx.L.maplibreGL = options => {vectorOptions.push(options);return {addTo(){if(failGL)throw Error('no WebGL');return this},getMaplibreMap:()=>gl,getContainer:()=>null}};
    }
    vm.createContext(ctx);vm.runInContext(code,ctx);
    const data = {locals:[{id:'1',name:'Local',lat:-33.02,lon:-71.56}],position:null,selected:null,dark};
    return {ctx,data,events,canvasEvents,timers,raster,styles,vectorOptions,elements,paints,fitOptions,
        fits:()=>fits,removed:()=>removed,workers:()=>workers,quality:()=>ctx.window.exploreMapQuality(),update:data=>ctx.window.updateExplore(data)};
}

test('el tema oscuro inicia directamente con su estilo, a doble resolución y sin invertir colores', () => {
    const a=app({dark:true});
    assert.equal(a.vectorOptions[0].style,'https://tiles.openfreemap.org/styles/dark');
    assert.equal(a.vectorOptions[0].pixelRatio,2);assert.equal(a.workers(),2);
    assert.equal(a.raster.length,0);assert.doesNotMatch(html,/invert\(|filter:var\(--tiles\)/);
    a.update(a.data);a.events.idle();
    assert.equal(a.quality().renderer,'vector');assert.equal(a.quality().ready,true);
    assert.equal(a.elements.status.hidden,true);
});
test('cambiar el tema conserva el encuadre y no vuelve a descargar el estilo en cada actualización', () => {
    const a=app();a.update(a.data);const fits=a.fits();
    a.update({...a.data,dark:true});a.update({...a.data,dark:true});
    assert.deepEqual(a.styles,['https://tiles.openfreemap.org/styles/dark']);assert.equal(a.fits(),fits);
    a.events.idle();assert.equal(a.quality().theme,'dark');assert.equal(a.quality().ready,true);
});
test('un teléfono de densidad normal no recibe un lienzo sobredimensionado', () => {
    assert.equal(app({ratio:1}).vectorOptions[0].pixelRatio,1);
});
test('el estilo oscuro mejora el contraste de las letras sin recolorear el estilo claro', () => {
    const a=app({dark:true});a.events['style.load']();
    assert(a.paints.some(p=>p.id==='highway_name_other'&&p.property==='text-color'&&p.value==='#c5cbd2'));
    assert(a.paints.some(p=>p.id==='background'&&p.value==='#1c1f23'));
    const light=app();light.events['style.load']();assert.equal(light.paints.length,0);
});
test('si WebGL no está disponible se conserva el mapa con imágenes retina', () => {
    const a=app({failGL:true});
    assert.equal(a.quality().renderer,'raster');assert.equal(a.removed(),1);
    assert.equal(a.raster[0].options.detectRetina,true);assert.equal(a.raster[0].options.keepBuffer,1);
    a.update(a.data);a.raster[0].events.load();assert.equal(a.elements.status.hidden,true);
});
test('si no se puede cargar el motor local, el respaldo sigue disponible', () => {
    assert.equal(app({vector:false}).raster.length,1);
});
test('perder el contexto gráfico activa una sola alternativa y descarta eventos del motor anterior', () => {
    const a=app();a.update(a.data);a.events.idle();
    a.canvasEvents.webglcontextlost();a.canvasEvents.webglcontextlost();a.events.idle();
    assert.equal(a.raster.length,1);assert.equal(a.quality().ready,false);
    a.raster[0].events.load();assert.equal(a.quality().ready,true);
});
test('un error de carga se limpia cuando los datos del mapa vuelven a estar disponibles', () => {
    const a=app();a.update(a.data);a.events.error();assert.equal(a.elements.status.textContent,'error');
    a.events.idle();assert.equal(a.elements.status.hidden,true);
});
test('la espera no oculta la instrucción de ubicación y una carga bloqueada activa el respaldo', () => {
    const a=app();a.update({...a.data,locals:[]});
    a.timers.find(t=>t.ms===12000).fn();assert.equal(a.elements.status.textContent,'origen');
    a.timers.find(t=>t.ms===20000).fn();assert.equal(a.quality().renderer,'raster');
});
test('mover la ficha no cambia la cámara y centrar deja una zona visible válida aunque la ficha esté ampliada', () => {
    const a=app();a.update({...a.data,topInset:228,bottomInset:500});
    const fit=a.fitOptions.at(-1);
    assert(fit.paddingTopLeft[1]+fit.paddingBottomRight[1]<=540);
    const previous=a.fits();a.update({...a.data,topInset:174,bottomInset:88});
    assert.equal(a.fits(),previous);
});
