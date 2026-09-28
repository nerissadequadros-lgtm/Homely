import { test } from 'node:test';
import assert from 'node:assert/strict';
import { manifestSchema } from '../src/manifest.js';

const valid = {
  schemaVersion: '1.5', title: 'Visual builder', category: 'Minimal', tags: [],
  palette: { accent: '#7965FF', background: '#090C12', surface: '#141922' },
  iconStyle: 'rounded', clockStyle: 'thin',
  assets: [{ id: '034048a1-f822-4093-abd9-bc23acbd64bb', kind: 'wallpaper', name: 'Wallpaper', url: '/media/a.png', mime: 'image/png', sha256: 'a'.repeat(64), size: 200 }],
  compatibility: { minAndroid: 26, launcherRequiredForIconPack: true, systemBars: 'preview-only' },
  license: 'CC-BY-4.0',
};
const baseLayout = { columns: 4, iconSize: 54, showLabels: true };
const gestureKeys = ['longPress', 'swipeUp', 'swipeDown', 'swipeLeft', 'swipeRight', 'tap', 'doubleTap', 'pinchIn', 'pinchOut', 'drawerSwipeDown'];

test('old manifests receive compatible builder defaults without losing old bindings', () => {
  for (const schemaVersion of ['1.0', '1.1', '1.2', '1.3', '1.4', '1.5']) {
    const parsed = manifestSchema.parse({ ...valid, schemaVersion, layout: { ...baseLayout, drawerLayout: 'list', gestures: { swipeDown: 'notifications' } } });
    assert.equal(parsed.layout.drawerLayout, 'list');
    assert.equal(parsed.layout.drawerMode, 'continuous');
    assert.equal(parsed.layout.drawerRows, 5);
    assert.equal(parsed.layout.drawerDirection, 'horizontal');
    assert.equal(parsed.layout.drawerOpacity, .94);
    assert.equal(parsed.layout.animationDuration, 280);
    assert.equal(parsed.layout.gestures.swipeDown, 'notifications');
    assert.equal(parsed.layout.gestures.drawerSwipeDown, 'close_drawer');
    assert.equal(parsed.layout.gestures.swipeLeft, 'next_page');
    assert.equal(parsed.layout.gestures.swipeRight, 'previous_page');
  }
});

test('publication parsing and JSON persistence retain every builder option and binding', () => {
  const layout = {
    ...baseLayout, drawerColumns: 5, drawerRows: 4, drawerSpacing: 6, drawerMargin: 20,
    drawerIconSize: 42, drawerMode: 'pages', drawerDirection: 'vertical', drawerAnimation: 'fade',
    animationDuration: 410, reduceMotion: true, drawerOpacity: .62, drawerBlur: 14,
    drawerRadius: 32, drawerShowSearch: false, hapticFeedback: false, drawerStyle: 'button',
    gestures: { longPress: 'wallpaper', iconLongPress: 'edit', swipeUp: 'drawer', swipeDown: 'widgets',
      swipeLeft: 'next_page', swipeRight: 'previous_page', tap: 'app', doubleTap: 'search',
      pinchIn: 'personalize', pinchOut: 'none', drawerSwipeDown: 'close_drawer', quickPackage: 'com.example.reader' },
  };
  const parsed = manifestSchema.parse({ ...valid, layout });
  const restored = manifestSchema.parse(JSON.parse(JSON.stringify(parsed)));
  for (const [key, value] of Object.entries(layout)) assert.deepEqual(restored.layout[key as keyof typeof restored.layout], value);
});

test('builder rejects invalid dimensions, effects, layout enums and boolean values', () => {
  const invalid: Record<string, unknown[]> = {
    drawerRows: [1, 11, 3.5], drawerColumns: [2, 9], drawerSpacing: [-1, 33],
    drawerMargin: [-1, 41], drawerIconSize: [29, 97], drawerOpacity: [.19, 1.01, NaN, Infinity],
    drawerBlur: [-1, 31], drawerRadius: [-1, 49], animationDuration: [-1, 801],
    drawerMode: ['carousel'], drawerDirection: ['diagonal'], drawerAnimation: ['spin'],
    drawerStyle: ['unknown'], drawerLayout: ['unknown'], folderStyle: ['unknown'],
    appAnimation: ['unknown'], scrollDirection: ['unknown'], quickAction: ['unknown'],
    reduceMotion: ['false'], drawerShowSearch: [0], hapticFeedback: [1],
    statusBar: [{ show: true, batteryIcon: 'custom', networkIcon: 'ios', clockFormat: '24h', tint: 'auto' }],
  };
  for (const [key, values] of Object.entries(invalid)) for (const value of values) {
    assert.equal(manifestSchema.safeParse({ ...valid, layout: { ...baseLayout, [key]: value } }).success, false, `${key}=${value}`);
  }
  for (const key of gestureKeys) {
    assert.equal(manifestSchema.safeParse({ ...valid, layout: { ...baseLayout, gestures: { [key]: 'execute_script' } } }).success, false, key);
  }
});

test('every app gesture and the quick button require a valid application package', () => {
  for (const key of gestureKeys) {
    for (const quickPackage of [undefined, null, 'bad/package']) {
      assert.equal(manifestSchema.safeParse({ ...valid, layout: { ...baseLayout, gestures: { [key]: 'app', quickPackage } } }).success, false, key);
    }
    assert.equal(manifestSchema.safeParse({ ...valid, layout: { ...baseLayout, gestures: { [key]: 'app', quickPackage: 'com.example.reader' } } }).success, true, key);
  }
  assert.equal(manifestSchema.safeParse({ ...valid, layout: { ...baseLayout, quickAction: 'app' } }).success, false);
  assert.equal(manifestSchema.safeParse({ ...valid, layout: { ...baseLayout, quickAction: 'app', gestures: { quickPackage: 'com.example.reader' } } }).success, true);
});
