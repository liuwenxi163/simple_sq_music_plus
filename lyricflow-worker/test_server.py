import importlib.util,os,pathlib,tempfile,unittest

class WorkerTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=pathlib.Path(self.tmp.name);(self.root/'music').mkdir()
        os.environ.update(MUSIC_PATH=str(self.root/'music'),STATE_DIR=str(self.root/'state'),LYRICFLOW_KEY='test-key-'*8)
        spec=importlib.util.spec_from_file_location('worker',pathlib.Path(__file__).with_name('server.py'));self.w=importlib.util.module_from_spec(spec);spec.loader.exec_module(self.w)
    def tearDown(self):self.tmp.cleanup()
    def test_reject_escape_and_bad_options(self):
        with self.assertRaises(ValueError):self.w.submit({'path':'..'})
        with self.assertRaises(ValueError):self.w.submit({'provider':'untrusted'})
    def test_coalesce_duplicate_and_persist(self):
        one=self.w.submit({});two=self.w.submit({});self.assertEqual(one['id'],two['id'])
        with self.w.connect() as c:self.assertEqual(c.execute('SELECT count(*) FROM jobs').fetchone()[0],1)
    def test_bound_pending_queue(self):
        with self.w.connect() as c:
            for n in range(256):c.execute('INSERT INTO jobs VALUES(?,?,?,?,?,?,?,?)',(str(n),'other/'+str(n),'tunehub',0,'queued','{}',0,0))
        with self.assertRaises(OverflowError):self.w.submit({})

if __name__=='__main__':unittest.main()
