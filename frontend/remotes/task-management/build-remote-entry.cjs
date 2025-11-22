
const fs = require('fs');
const path = require('path');
const dist = path.join(__dirname, 'dist', 'browser');

// Read built files
const polyfills = fs.readFileSync(path.join(dist, 'polyfills.js'), 'utf8');
const main = fs.readFileSync(path.join(dist, 'main.js'), 'utf8');

// Create remoteEntry.js: polyfills guard + main bundle concatenated
const remoteEntry = `
/* NebulaOps v22.2 - MFE Remote Entry - Angular Elements bundle */
/* Polyfills (zone.js) - skip if already loaded by shell */
if (typeof Zone === 'undefined') {
${polyfills}
}
/* Main Angular Elements bundle */
${main}
`;

fs.writeFileSync(path.join(dist, 'remoteEntry.js'), remoteEntry);
console.log('✅ remoteEntry.js created (' + Math.round(remoteEntry.length / 1024) + ' kB)');
