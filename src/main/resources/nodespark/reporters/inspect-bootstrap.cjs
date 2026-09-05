// Loaded through NODE_OPTIONS=--require when NodeSpark debugs a package.json script.
//
// --inspect-brk cannot be used there: the package manager is itself a node process, so it would be
// the one that stopped on line one. Instead every node process in the tree loads this file, and the
// first one that is not a package manager opens the inspector and waits for the IDE to attach.
const port = Number(process.env.NODESPARK_DEBUG_PORT || 0);

if (port) {
  const main = String(process.argv[1] || '').replace(/\\/g, '/');
  const isPackageManager =
    /\/(npm-cli|npx-cli|npm|npx|yarn|pnpm|pnpx|corepack)(\.[cm]?js)?$/i.test(main) ||
    /\/node_modules\/(npm|yarn|pnpm|corepack)\//i.test(main);

  if (!isPackageManager) {
    // Cleared before the inspector opens: any child this script spawns must not fight for the port.
    process.env.NODESPARK_DEBUG_PORT = '';
    const inspector = require('inspector');
    if (!inspector.url()) inspector.open(port, '127.0.0.1', true); // true = block until the IDE attaches
  }
}
