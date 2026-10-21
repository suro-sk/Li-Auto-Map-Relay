"""Two live relay tasks must not resurrect a location deleted in the other task."""
import re
import emulator_test as t
from emulator_features_test import AMAP,tap

assert t.adb('emu','avd','name').splitlines()[0].strip()=='MapRelay_Test'
t.shell('pm','clear','com.autonavi.minimap')
t.start(AMAP);tap('Settings');tap('Save recent locations');tap('Back')
activities=t.shell('dumpsys','activity','activities')
task=int(re.search(r'topResumedActivity=ActivityRecord\{[^\n]* t(\d+)',activities).group(1))
t.shell('am','start','-W','-f','0x18000000','-a','android.intent.action.VIEW','-n','com.autonavi.minimap/org.li.maprelay.RelayActivity','-d',AMAP.replace('My%20car','Second%20car'))
tap('Settings');tap('Clear history')
try:
    t.shell('am','task','lock',task)
finally:
    t.shell('am','task','lock','stop')
tap('Settings');tap('Coordinate format:',True);tap('WGS84 (no correction)');tap('Back');tap('Recent locations')
assert t.text_present('No saved locations yet'),'A different Activity resurrected deleted history'
print('PASS: Deleted history is not restored by a second live Activity',flush=True)
