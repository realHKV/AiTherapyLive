const fs = require('fs');

const colors = {
    "surface": "#ffffff",
    "secondary-fixed-dim": "#c0d9db",
    "surface-bright": "#ffffff",
    "on-tertiary-container": "#515965",
    "outline": "#637ca2",
    "primary-fixed-dim": "#ffc8bb",
    "tertiary-fixed-dim": "#d8e0ef",
    "on-tertiary-fixed": "#3f4753",
    "secondary": "#4c6365",
    "surface-container-low": "#f0f3ff",
    "on-secondary": "#e7fdff",
    "on-error-container": "#752121",
    "on-secondary-container": "#3f5658",
    "surface-container-lowest": "#ffffff",
    "surface-container-highest": "#d4e3ff",
    "secondary-container": "#cee7e9",
    "on-tertiary": "#f7f9ff",
    "error-container": "#fe8983",
    "on-background": "#2d2d2d",
    "primary-dim": "#9f2100",
    "error-dim": "#4e0309",
    "on-primary-fixed": "#7d1800",
    "on-surface": "#2d2d2d",
    "surface-tint": "#b52701",
    "secondary-fixed": "#cee7e9",
    "tertiary-fixed": "#e6eefd",
    "tertiary": "#57606c",
    "outline-variant": "#9ab3dd",
    "error": "#9f403d",
    "inverse-primary": "#fc5a33",
    "primary-container": "#ffdad2",
    "on-secondary-fixed-variant": "#496061",
    "surface-dim": "#c6dbff",
    "on-tertiary-fixed-variant": "#5b6370",
    "surface-variant": "#d4e3ff",
    "on-surface-variant": "#5a5a5a",
    "on-primary": "#fff6f4",
    "inverse-on-surface": "#959dad",
    "on-secondary-fixed": "#2d4345",
    "inverse-surface": "#070e1a",
    "secondary-dim": "#405759",
    "tertiary-container": "#e6eefd",
    "surface-container-high": "#dde9ff",
    "tertiary-dim": "#4b5460",
    "primary": "#ff6b00",
    "on-primary-container": "#ff6b00",
    "on-error": "#fff7f6",
    "background": "#ffffff",
    "surface-container": "#e7eeff",
    "on-primary-fixed-variant": "#b02600",
    "primary-fixed": "#ffdad2"
};

function hexToRgb(hex) {
    if(!hex) return '0 0 0';
    hex = hex.replace('#', '');
    if(hex.length === 3) hex = hex.split('').map(c => c+c).join('');
    const r = parseInt(hex.substring(0,2), 16);
    const g = parseInt(hex.substring(2,4), 16);
    const b = parseInt(hex.substring(4,6), 16);
    return `${r} ${g} ${b}`;
}

function getDarkColor(r, g, b, key) {
    if (key === 'background' || key === 'surface' || key === 'surface-bright' || key === 'surface-container-lowest') return '10 14 23';
    if (key === 'surface-container-low') return '16 23 38';
    if (key === 'surface-container') return '21 31 51';
    if (key === 'surface-container-high') return '28 41 66';
    if (key === 'surface-container-highest') return '36 53 84';
    if (key === 'surface-variant') return '36 53 84';
    if (key === 'on-surface' || key === 'on-background') return '235 240 255';
    if (key === 'on-surface-variant') return '160 175 200';
    if (key === 'outline') return '75 85 105';
    if (key === 'outline-variant') return '45 55 75';
    
    if (key === 'primary') return '255 125 50'; 
    if (key === 'primary-container') return '75 25 0';
    if (key === 'on-primary-container') return '255 200 170';

    const brightness = (0.299*r + 0.587*g + 0.114*b);
    if (brightness > 200) {
        return Math.floor(r*0.1) + ' ' + Math.floor(g*0.1) + ' ' + Math.floor(b*0.1);
    } else if (brightness < 80) {
        return Math.floor(r + (255-r)*0.8) + ' ' + Math.floor(g + (255-g)*0.8) + ' ' + Math.floor(b + (255-b)*0.8);
    }
    return Math.floor(r*0.8) + ' ' + Math.floor(g*0.8) + ' ' + Math.floor(b*0.8);
}

let rootVars = [':root {'];
let darkVars = ['.dark {'];
let twConfigColors = [];

for(const [k, v] of Object.entries(colors)) {
    const rgb = hexToRgb(v);
    const [r,g,b] = rgb.split(' ').map(Number);
    const darkRgb = getDarkColor(r,g,b,k);
    
    rootVars.push(`  --color-${k}: ${rgb};`);
    darkVars.push(`  --color-${k}: ${darkRgb};`);
    twConfigColors.push(`        "${k}": "rgb(var(--color-${k}) / <alpha-value>)",`);
}
rootVars.push('}');
darkVars.push('}');

console.log('--- CSS Variables ---');
console.log(rootVars.join('\n'));
console.log(darkVars.join('\n'));
console.log('\n--- Tailwind Config Colors ---');
console.log(twConfigColors.join('\n'));
